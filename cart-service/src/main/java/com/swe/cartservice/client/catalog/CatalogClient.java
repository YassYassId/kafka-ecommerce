package com.swe.cartservice.client.catalog;

import com.swe.cartservice.dto.CatalogProductResponse;

import java.util.UUID;

public interface CatalogClient {
    CatalogProductResponse getProduct(UUID productId);
}
