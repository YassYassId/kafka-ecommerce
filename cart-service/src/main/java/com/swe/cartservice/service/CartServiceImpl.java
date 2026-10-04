package com.swe.cartservice.service;

import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.exception.CartItemNotFoundException;
import com.swe.cartservice.exception.CartNotFoundException;
import com.swe.cartservice.metrics.CartMetrics;
import com.swe.cartservice.model.Cart;
import com.swe.cartservice.model.CartItem;
import com.swe.cartservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartMetrics cartMetrics;

    @Override
    public Cart getCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> emptyCart(customerId));
    }

    @Override
    public Cart addItem(UUID customerId, AddCartItemRequest request) {
        List<CartItem> items = new ArrayList<>(
                cartRepository.findByCustomerId(customerId)
                        .map(Cart::items)
                        .orElseGet(List::of)
        );

        Optional<CartItem> existingItem = items.stream()
                .filter(item -> item.productId().equals(request.productId()))
                .findFirst();

        if (existingItem.isPresent()) {
            int index = items.indexOf(existingItem.get());
            items.set(index, new CartItem(
                    request.productId(),
                    existingItem.get().quantity() + request.quantity(),
                    request.price()
            ));
        } else {
            items.add(new CartItem(
                    request.productId(),
                    request.quantity(),
                    request.price()
            ));
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

    private Cart emptyCart(UUID customerId) {
        return new Cart(customerId, List.of(), OffsetDateTime.now());
    }
}
