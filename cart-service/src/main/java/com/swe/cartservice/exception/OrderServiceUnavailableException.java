package com.swe.cartservice.exception;

public class OrderServiceUnavailableException extends RuntimeException {
    public OrderServiceUnavailableException() {
        super("Orders service is temporarily unavailable");
    }

    public OrderServiceUnavailableException(Throwable cause) {
        super("Orders service is temporarily unavailable", cause);
    }
}
