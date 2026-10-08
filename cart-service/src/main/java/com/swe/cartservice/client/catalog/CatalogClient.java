package com.swe.cartservice.catalog;

import com.swe.cartservice.dto.CatalogProductResponse;

import java.util.UUID;

public interface CatalogClient {
    CatalogProductResponse getProduct(UUID productId);
}
