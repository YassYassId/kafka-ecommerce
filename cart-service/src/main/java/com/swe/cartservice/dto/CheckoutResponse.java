package com.swe.cartservice.dto;

import java.util.UUID;

public record CheckoutResponse(
        UUID orderId,
        String status
) {
}
