package com.swe.catalogservice.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        String description,

        @NotBlank
        @Size(max = 100)
        String category,

        @NotNull
        @DecimalMin(value = "0.00")
        BigDecimal price,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$")
        String currency
) {
}
