package com.swe.cartservice.service;

import com.swe.cartservice.client.catalog.CatalogClient;
import com.swe.cartservice.client.order.*;
import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.dto.CatalogProductResponse;
import com.swe.cartservice.dto.CheckoutResponse;
import com.swe.cartservice.dto.ProductStatus;
import com.swe.cartservice.exception.*;
import com.swe.cartservice.metrics.CartMetrics;
import com.swe.cartservice.model.*;
import com.swe.cartservice.repository.CartRepository;
import com.swe.cartservice.repository.CheckoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartMetrics cartMetrics;
    private final CatalogClient catalogClient;
    private final OrderClient orderClient;
    private final CheckoutRepository checkoutRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Cart getCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> emptyCart(customerId));
    }

    @Override
    public Cart addItem(UUID customerId, AddCartItemRequest request) {

        CatalogProductResponse product = catalogClient.getProduct(request.productId());

        if (product.status() != ProductStatus.ACTIVE) {
            throw new ProductNotAvailableException(request.productId());
        }

        String expectedCartJson = cartRepository.findRawCartByCustomerId(customerId).orElse("");

        Cart existingCart = expectedCartJson.isEmpty() ? emptyCart(customerId) : objectMapper.readValue(expectedCartJson, Cart.class);

        var items = new ArrayList<>(existingCart.items());

        var existingItem = items.stream()
                .filter(item -> item.productId().equals(request.productId()))
                .findFirst();

        if (existingItem.isPresent()) {

            CartItem current = existingItem.get();

            items.remove(current);

            items.add(new CartItem(current.productId(), current.quantity() + request.quantity(), product.price()));

        } else {

            items.add(new CartItem(request.productId(), request.quantity(), product.price()));
        }

        Cart updatedCart = new Cart(customerId, List.copyOf(items), OffsetDateTime.now());

        requireSuccessfulMutation(cartRepository.saveIfUnchanged(updatedCart, expectedCartJson));
        cartMetrics.itemAdded();

        return updatedCart;
    }

    @Override
    public Cart updateItemQuantity(UUID customerId, UUID productId, int quantity) {
        String expectedCartJson = cartRepository.findRawCartByCustomerId(customerId)
                .orElseThrow(() -> new CartNotFoundException(customerId));

        Cart cart = objectMapper.readValue(expectedCartJson, Cart.class);

        List<CartItem> items = new ArrayList<>(cart.items());

        Optional<CartItem> existingItem = items.stream()
                .filter(item -> item.productId().equals(productId))
                .findFirst();

        if (existingItem.isEmpty()) {
            throw new CartItemNotFoundException(productId);
        }

        int index = items.indexOf(existingItem.get());
        items.set(index, new CartItem(
                productId,
                quantity,
                existingItem.get().price()
        ));

        Cart updatedCart = new Cart(customerId, List.copyOf(items), OffsetDateTime.now());

        requireSuccessfulMutation(cartRepository.saveIfUnchanged(updatedCart, expectedCartJson));
        cartMetrics.itemUpdated();

        return updatedCart;
    }

    @Override
    public Cart removeItem(UUID customerId, UUID productId) {
        String expectedCartJson = cartRepository.findRawCartByCustomerId(customerId)
                .orElseThrow(() -> new CartNotFoundException(customerId));

        Cart cart = objectMapper.readValue(expectedCartJson, Cart.class);

        List<CartItem> items = new ArrayList<>(cart.items());

        boolean removed = items.removeIf(item -> item.productId().equals(productId));

        if (!removed) {
            throw new CartItemNotFoundException(productId);
        }

        if (items.isEmpty()) {
            requireSuccessfulMutation(cartRepository.deleteIfUnchanged(customerId, expectedCartJson));

            cartMetrics.itemRemoved();
            return emptyCart(customerId);
        }

        Cart updatedCart = new Cart(customerId, List.copyOf(items), OffsetDateTime.now());

        requireSuccessfulMutation(cartRepository.saveIfUnchanged(updatedCart, expectedCartJson));
        cartMetrics.itemRemoved();

        return updatedCart;
    }


    @Override
    public void clearCart(UUID customerId) {

        String expectedCartJson = cartRepository.findRawCartByCustomerId(customerId).orElse("");

        requireSuccessfulMutation(cartRepository.deleteIfUnchanged(customerId, expectedCartJson));

        cartMetrics.cartCleared();
    }

    @Override
    public CheckoutResponse checkout(UUID customerId, String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }

        // 1. Replay an already completed checkout.
        Optional<CheckoutRecord> previous = checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey);

        if (previous.isPresent()) {
            CheckoutRecord record = previous.get();

            log.info("Replaying completed checkout: customerId={}, orderId={}", customerId, record.orderId());

            return new CheckoutResponse(record.orderId(), record.status());
        }

        // 2. Generate a unique token for this execution attempt.
        String leaseToken = UUID.randomUUID().toString();

        // 3. Read the current cart if it exists.
        // An existing durable claim may already contain the original snapshot.
        String currentCartJson = cartRepository.findRawCartByCustomerId(customerId).orElse("");

        // 4. Atomically acquire or resume the checkout.
        CheckoutClaimResult claimResult = checkoutRepository.acquireCheckout(customerId, idempotencyKey, currentCartJson, leaseToken);

        log.info("Checkout acquisition: customerId={}, result={}", customerId, claimResult);

        switch (claimResult) {
            case BUSY, CONFLICT -> throw new CheckoutConflictException();

            case CART_CHANGED -> {
                if (currentCartJson.isEmpty()) {
                    throw new CartNotFoundException(customerId);
                }
                throw new CheckoutConflictException();
            }

            case ACQUIRED, RESUMED -> {
                // Continue with checkout.
            }
        }

        // 5. Retrieve the durable claim containing the original cart snapshot.
        CheckoutClaim claim = checkoutRepository.findClaim(customerId)
                .orElseThrow(() -> new IllegalStateException("Checkout claim disappeared after acquisition"));

        if (!idempotencyKey.equals(claim.idempotencyKey())) {
            throw new CheckoutConflictException();
        }

        String originalCartJson = claim.cartSnapshot();

        // 6. Reconstruct the cart from the exact claimed snapshot.
        // Never reread a potentially modified cart to build the Order request.
        Cart cart = objectMapper.readValue(originalCartJson, Cart.class);

        if (!customerId.equals(cart.customerId())) {
            throw new CheckoutConflictException();
        }

        // 7. Build the exact Order request from the durable cart snapshot.
        // This does not require Catalog Service.
        if (cart.items().isEmpty()) {

            if (claimResult == CheckoutClaimResult.ACQUIRED) {
                checkoutRepository.releaseClaim(customerId, idempotencyKey, leaseToken);
            }

            throw new EmptyCartException(customerId);
        }

        List<CreateOrderItemRequest> orderItems = cart.items().stream()
                .map(item -> new CreateOrderItemRequest(item.productId(), item.quantity()))
                .toList();

        CreateOrderRequest orderRequest = new CreateOrderRequest(customerId, List.copyOf(orderItems));

        OrderResponse order = null;

        // 8. A resumed checkout may already have created an Order.
        //
        // Reconcile BEFORE Catalog validation because the Order may already
        // exist even if the product has since become unavailable.
        if (claimResult == CheckoutClaimResult.RESUMED) {

            log.info("Reconciling resumed checkout: customerId={}", customerId);

            OrderReconciliationResult reconciliation = orderClient.reconcileCheckout(idempotencyKey, orderRequest);

            if (reconciliation.found()) {

                order = reconciliation.order()
                        .orElseThrow(() -> new IllegalStateException("Reconciliation reported an Order but returned none"));

                log.info("Recovered existing Order: customerId={}, orderId={}", customerId, order.orderId());
            }
        }

        // 9. No existing Order was recovered.
        //
        // For a fresh checkout this is always executed.
        // For a resumed checkout it is executed only when reconciliation
        // returned 404.
        if (order == null) {

            try {

                for (CartItem item : cart.items()) {

                    CatalogProductResponse product = catalogClient.getProduct(item.productId());

                    if (product.status() != ProductStatus.ACTIVE) {
                        throw new ProductNotAvailableException(item.productId());
                    }
                }

            } catch (RuntimeException ex) {

                // Releasing the durable claim is safe only for a fresh checkout.
                //
                // For RESUMED, even after reconciliation returned 404,
                // an earlier Order request could still be committing.
                if (claimResult == CheckoutClaimResult.ACQUIRED) {

                    checkoutRepository.releaseClaim(customerId, idempotencyKey, leaseToken);
                }

                throw ex;
            }

            // Orders Service remains the final idempotency authority.
            order = orderClient.createOrder(idempotencyKey, orderRequest);

            log.info("Order created/replayed: customerId={}, orderId={}", customerId, order.orderId());
        }

        // 10. Whether created normally or recovered through reconciliation,
        // complete the Redis checkout in exactly the same way.
        CheckoutRecord record = new CheckoutRecord(customerId, idempotencyKey, order.orderId(), order.status());

        boolean completed = checkoutRepository.completeCheckout(record, originalCartJson, leaseToken);

        log.info("Checkout completion: customerId={}, completed={}", customerId, completed);

        if (!completed) {

            // Never release the durable claim here.
            //
            // At this point an Order definitely exists in PostgreSQL.
            // A later retry must be able to reconcile it.
            throw new CheckoutConflictException();
        }

        return new CheckoutResponse(order.orderId(), order.status());
    }

    private Cart emptyCart(UUID customerId) {
        return new Cart(customerId, List.of(), OffsetDateTime.now());
    }


    private void requireSuccessfulMutation(CartMutationResult result) {
        switch (result) {
            case SUCCESS -> {
                // Mutation completed.
            }
            case CHECKOUT_IN_PROGRESS ->
                    throw new CheckoutConflictException();
            case STALE_CART ->
                    throw new CartConcurrentModificationException();
        }
    }

}
