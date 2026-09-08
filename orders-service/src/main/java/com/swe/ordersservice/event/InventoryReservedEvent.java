package com.swe.ordersservice.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedEvent(
        UUID eventId,
        UUID orderId,
        Instant occuredAt,
        int version
) {
}
