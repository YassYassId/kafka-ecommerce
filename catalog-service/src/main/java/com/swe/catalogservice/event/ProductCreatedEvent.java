package com.swe.catalogservice.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductCreatedEvent(
        UUID eventId,
        UUID productId,
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price,
        String currency,
        OffsetDateTime occurredAt,
        int version
) {
}
