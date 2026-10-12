package com.swe.cartservice.exception;

public class CheckoutConflictException extends RuntimeException {

    public CheckoutConflictException() {
        super("Cart changed during checkout. Please review your cart and retry.");
    }
}