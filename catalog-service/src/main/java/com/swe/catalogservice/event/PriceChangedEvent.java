package com.swe.catalogservice.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PriceChangedEvent(
        UUID eventId,
        UUID productId,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        String currency,
        OffsetDateTime occurredAt,
        int version
) {
}
