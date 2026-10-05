package com.swe.cartservice.exception;

public class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException() {
        super("Catalog service is temporarily unavailable");
    }

    public CatalogUnavailableException(Throwable cause) {
        super("Catalog service is temporarily unavailable", cause);
    }
}
