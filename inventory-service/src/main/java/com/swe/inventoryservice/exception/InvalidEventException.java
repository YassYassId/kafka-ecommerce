package com.swe.inventoryservice.exception;

public class InvalidEventException extends RuntimeException{
    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
