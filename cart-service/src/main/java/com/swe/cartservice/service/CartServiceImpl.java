package com.swe.cartservice.service;

import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.exception.CartItemNotFoundException;
import com.swe.cartservice.exception.CartNotFoundException;
import com.swe.cartservice.model.Cart;
import com.swe.cartservice.model.CartItem;
import com.swe.cartservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;

    @Override
    public Cart getCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId).orElseGet(() -> emptyCart(customerId));
    }

    @Override
    public Cart addItem(UUID customerId, AddCartItemRequest request) {

        Cart existingCart = getCart(customerId);

        var items = new ArrayList<>(existingCart.items());

        var existingItem = items.stream().filter(item -> item.productId().equals(request.productId()))
                .findFirst();

        if (existingItem.isPresent()) {

            CartItem current = existingItem.get();

            items.remove(current);

            items.add(new CartItem(current.productId(), current.quantity() + request.quantity(), request.price()));

        } else {

            items.add(new CartItem(request.productId(), request.quantity(), request.price()));
        }

        Cart updatedCart = new Cart(customerId, List.copyOf(items), OffsetDateTime.now());

        cartRepository.save(updatedCart);

        return updatedCart;
    }

    private Cart emptyCart(UUID customerId) {
        return new Cart(customerId, List.of(), OffsetDateTime.now());
    }

    public Cart updateItemQuantity(UUID customerId, UUID productId, int quantity) {

        Cart cart = cartRepository.findByCustomerId(customerId).orElseThrow(() -> new CartNotFoundException(customerId));

        var items = new ArrayList<>(cart.items());

        CartItem existingItem = items.stream()
                .filter(item -> item.productId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException(productId));

        items.remove(existingItem);

        items.add(new CartItem(existingItem.productId(), quantity, existingItem.price()));

        Cart updatedCart = new Cart(customerId, List.copyOf(items), OffsetDateTime.now());

        cartRepository.save(updatedCart);

        return updatedCart;
    }

    @Override
    public Cart removeItem(UUID customerId, UUID productId) {

        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CartNotFoundException(customerId));

        var items = new ArrayList<>(cart.items());

        boolean removed = items.removeIf(item -> item.productId().equals(productId));

        if (!removed) {
            throw new CartItemNotFoundException(productId);
        }

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
    }
}
