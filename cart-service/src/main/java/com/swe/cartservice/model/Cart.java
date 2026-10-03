package com.swe.cartservice.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record Cart(
        UUID customerId,
        List<CartItem> items,
        OffsetDateTime updatedAt
) {
}
