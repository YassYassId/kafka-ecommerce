package com.swe.notificationsservice.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedEvent(
        UUID eventId,
        UUID orderId,
        Instant occurredAt,
        int version
) {
}
