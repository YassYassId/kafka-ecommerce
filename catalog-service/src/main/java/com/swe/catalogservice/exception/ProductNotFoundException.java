package com.swe.catalogservice.exception;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException{
    public ProductNotFoundException(UUID id) {
        super("Product with ID: '" + id + "' was not found");
    }
}
