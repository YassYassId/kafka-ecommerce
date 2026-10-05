package com.swe.cartservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogProductResponse(
        UUID id,
        BigDecimal price,
        String currency,
        ProductStatus status
) {
}
