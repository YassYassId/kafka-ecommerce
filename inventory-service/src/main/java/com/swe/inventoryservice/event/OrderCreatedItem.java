package com.swe.inventoryservice.event;

import java.util.UUID;

public record OrderCreatedItem(
        UUID productId,
        int quantity
) {
}
