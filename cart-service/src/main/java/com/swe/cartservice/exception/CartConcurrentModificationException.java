package com.swe.cartservice.exception;

public class CartConcurrentModificationException extends RuntimeException {

    public CartConcurrentModificationException() {
        super("Cart was modified concurrently. Please retry.");
    }
}
