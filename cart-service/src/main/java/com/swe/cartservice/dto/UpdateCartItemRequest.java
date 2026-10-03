package com.swe.cartservice.dto;

import jakarta.validation.constraints.Positive;

public record UpdateCartItemRequest(
        @Positive
        int quantity
) {
}
