package com.swe.cartservice.exception;

import java.util.UUID;

public class ProductNotAvailableException extends RuntimeException {
    public ProductNotAvailableException(UUID productId) {
        super("Product is not available: " + productId);
    }
}
