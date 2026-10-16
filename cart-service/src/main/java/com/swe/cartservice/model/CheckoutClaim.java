package com.swe.cartservice.model;

public record CheckoutClaim(
        String idempotencyKey,
        String cartSnapshot
) {
}
