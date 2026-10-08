package com.swe.cartservice.exception;

import java.util.UUID;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException(UUID customerId) {
        super("Cannot checkout an empty cart for customer: " + customerId);
    }
}