package com.swe.ordersservice.event;

import java.util.UUID;

public record OrderCreatedItem(
        UUID productId,
        int quantity
) {
}
