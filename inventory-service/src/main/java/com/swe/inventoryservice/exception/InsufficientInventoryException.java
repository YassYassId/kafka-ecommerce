package com.swe.inventoryservice.exception;

import java.util.UUID;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(UUID productId) {
        super("Insufficient inventory for product: " + productId);
    }
}
