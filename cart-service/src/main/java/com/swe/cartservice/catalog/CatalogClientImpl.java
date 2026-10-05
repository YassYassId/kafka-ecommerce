package com.swe.cartservice.catalog;

import com.swe.cartservice.dto.CatalogProductResponse;
import com.swe.cartservice.exception.CatalogUnavailableException;
import com.swe.cartservice.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CatalogClientImpl implements CatalogClient {

    private final RestClient catalogRestClient;

    @Override
    public CatalogProductResponse getProduct(UUID productId) {
        try {
            return catalogRestClient.get()
                    .uri("/api/v1/products/{productId}", productId)
                    .retrieve()
                    .body(CatalogProductResponse.class);

        } catch (HttpClientErrorException.NotFound ex) {

            throw new ProductNotFoundException(productId);

        } catch (HttpServerErrorException | ResourceAccessException ex) {

            throw new CatalogUnavailableException(ex);
        }
    }
}
