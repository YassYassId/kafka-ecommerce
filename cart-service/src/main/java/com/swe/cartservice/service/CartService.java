package com.swe.cartservice.service;

import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.model.Cart;

import java.util.UUID;

public interface CartService {

    Cart getCart(UUID customerId);

    Cart addItem(UUID customerId, AddCartItemRequest request);

    Cart updateItemQuantity(UUID customerId, UUID productId, int quantity);

    Cart removeItem(UUID customerId, UUID productId);

    void clearCart(UUID customerId);
}
