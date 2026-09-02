package com.swe.ordersservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(OffsetDateTime.now(), status, error, message, path, null);
    }

    public ErrorResponse(int status, String error, String message, String path, List<FieldErrorDetail> fieldErrors) {
        this(OffsetDateTime.now(), status, error, message, path, fieldErrors);
    }
}
