package com.swe.cartservice.service;

import com.swe.cartservice.client.catalog.CatalogClient;
import com.swe.cartservice.client.order.CreateOrderItemRequest;
import com.swe.cartservice.client.order.CreateOrderRequest;
import com.swe.cartservice.client.order.OrderClient;
import com.swe.cartservice.client.order.OrderResponse;
import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.dto.CatalogProductResponse;
import com.swe.cartservice.dto.CheckoutResponse;
import com.swe.cartservice.dto.ProductStatus;
import com.swe.cartservice.exception.*;
import com.swe.cartservice.metrics.CartMetrics;
import com.swe.cartservice.model.Cart;
import com.swe.cartservice.model.CartItem;
import com.swe.cartservice.model.CheckoutRecord;
import com.swe.cartservice.repository.CartRepository;
import com.swe.cartservice.repository.CheckoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    @Override
    public Cart getCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> emptyCart(customerId));
    }

    @Override
    public Cart addItem(UUID customerId, AddCartItemRequest request) {

        CatalogProductResponse product =
                catalogClient.getProduct(request.productId());

        if (product.status() != ProductStatus.ACTIVE) {
            throw new ProductNotAvailableException(request.productId());
        }

        Cart existingCart = getCart(customerId);

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

        cartRepository.save(updatedCart);
        cartMetrics.itemAdded();

        return updatedCart;
    }

    @Override
    public Cart updateItemQuantity(UUID customerId, UUID productId, int quantity) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CartNotFoundException(customerId));

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

        cartRepository.save(updatedCart);
        cartMetrics.itemUpdated();

        return updatedCart;
    }

    @Override
    public Cart removeItem(UUID customerId, UUID productId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CartNotFoundException(customerId));

        List<CartItem> items = new ArrayList<>(cart.items());

        boolean removed = items.removeIf(item -> item.productId().equals(productId));

        if (!removed) {
            throw new CartItemNotFoundException(productId);
        }

        cartMetrics.itemRemoved();

        if (items.isEmpty()) {
            cartRepository.deleteByCustomerId(customerId);
            return emptyCart(customerId);
        }

        Cart updatedCart = new Cart(customerId, List.copyOf(items), OffsetDateTime.now());

        cartRepository.save(updatedCart);

        return updatedCart;
    }

    @Override
    public void clearCart(UUID customerId) {
        cartRepository.deleteByCustomerId(customerId);
        cartMetrics.cartCleared();
    }

    @Override
    public CheckoutResponse checkout(UUID customerId, String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }

        // 1. Return a previous completed checkout.
        Optional<CheckoutRecord> previous = checkoutRepository.findByCustomerIdAndKey(customerId, idempotencyKey);

        if (previous.isPresent()) {
            CheckoutRecord record = previous.get();

            log.info("Replaying completed checkout: customerId={}, orderId={}",
                    customerId, record.orderId());

            return new CheckoutResponse(record.orderId(), record.status());
        }

        // 2. Read the original cart snapshot.
        String originalCartJson = cartRepository.findRawCartByCustomerId(customerId)
                .orElseThrow(() -> new CartNotFoundException(customerId));

        // 3. Acquire checkout claim BEFORE calling Orders.
        boolean claimed = checkoutRepository.claimCheckout(customerId, idempotencyKey, originalCartJson);

        log.info("Checkout claim: customerId={}, key={}, claimed={}", customerId, idempotencyKey, claimed);

        if (!claimed) {
            throw new CheckoutConflictException();
        }

        CreateOrderRequest orderRequest;

        // 4. Validate cart and products.
        // These operations happen before contacting Orders.
        try {
            Cart cart = cartRepository.findByCustomerId(customerId)
                    .orElseThrow(() -> new CartNotFoundException(customerId));

            if (cart.items().isEmpty()) {
                throw new EmptyCartException(customerId);
            }

            List<CreateOrderItemRequest> orderItems = new ArrayList<>();

            for (CartItem item : cart.items()) {

                CatalogProductResponse product = catalogClient.getProduct(item.productId());

                if (product.status() != ProductStatus.ACTIVE) {
                    throw new ProductNotAvailableException(item.productId());
                }

                orderItems.add(new CreateOrderItemRequest(item.productId(), item.quantity()));
            }

            orderRequest = new CreateOrderRequest(customerId, List.copyOf(orderItems));

        } catch (RuntimeException ex) {

            // Safe to release because Orders has not been contacted.
            checkoutRepository.releaseClaim(customerId, idempotencyKey);

            throw ex;
        }

        // 5. Create or replay the Order.
        // Do NOT automatically release the claim after this call starts.
        OrderResponse order = orderClient.createOrder(idempotencyKey, orderRequest);

        log.info("Order created/replayed: customerId={}, orderId={}", customerId, order.orderId());

        // 6. Atomically persist checkout result, delete cart, release claim.
        CheckoutRecord record = new CheckoutRecord(customerId, idempotencyKey, order.orderId(), order.status());

        boolean completed = checkoutRepository.completeCheckout(record, originalCartJson);

        log.info("Checkout completion: customerId={}, completed={}", customerId, completed);

        if (!completed) {
            throw new CheckoutConflictException();
        }

        return new CheckoutResponse(order.orderId(), order.status());
    }

    private Cart emptyCart(UUID customerId) {
        return new Cart(customerId, List.of(), OffsetDateTime.now());
    }
}
