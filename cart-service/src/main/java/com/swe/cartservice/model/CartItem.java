package com.swe.cartservice.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItem(
        UUID productId,
        int quantity,
        BigDecimal price
) {
}
