package com.swe.notificationsservice.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryRejectedEvent(
        UUID eventId,
        UUID orderId,
        UUID productId,
        int requestedQuantity,
        int availableQuantity,
        String reason,
        Instant occurredAt,
        int version
) {
}
