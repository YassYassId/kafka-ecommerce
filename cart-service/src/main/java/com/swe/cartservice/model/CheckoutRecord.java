package com.swe.cartservice.model;

import java.util.UUID;

public record CheckoutRecord(
        UUID customerId,
        String idempotencyKey,
        UUID orderId,
        String status
) {
}
