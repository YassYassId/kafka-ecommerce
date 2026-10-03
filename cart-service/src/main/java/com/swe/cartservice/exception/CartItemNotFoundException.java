package com.swe.cartservice.exception;

import java.util.UUID;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(UUID productId) {
        super("Product not found in cart: " + productId);
    }
}
