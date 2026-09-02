package com.swe.ordersservice.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        List<OrderCreatedItem> items,
        Instant occurredAt,
        int version
) {
}
