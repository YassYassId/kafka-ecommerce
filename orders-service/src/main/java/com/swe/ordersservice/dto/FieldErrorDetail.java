package com.swe.ordersservice.dto;

public record FieldErrorDetail(
        String field,
        String message,
        Object rejectedValue
) {
}
