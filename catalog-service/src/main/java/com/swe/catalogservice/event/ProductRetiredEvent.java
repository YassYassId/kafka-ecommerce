package com.swe.catalogservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductRetiredEvent(
        UUID eventId,
        UUID productId,
        String sku,
        OffsetDateTime occurredAt,
        int version
) {
}
