package com.swe.catalogservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductUpdatedEvent(
        UUID eventId,
        UUID productId,
        String sku,
        String name,
        String description,
        String category,
        String currency,
        OffsetDateTime occurredAt,
        int version
) {
}
