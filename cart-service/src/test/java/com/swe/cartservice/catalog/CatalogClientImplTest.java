package com.swe.cartservice.catalog;

import com.swe.cartservice.dto.CatalogProductResponse;
import com.swe.cartservice.dto.ProductStatus;
import com.swe.cartservice.exception.CatalogUnavailableException;
import com.swe.cartservice.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogClientImplTest {

    private MockRestServiceServer mockServer;
    private CatalogClientImpl catalogClient;
    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        catalogClient = new CatalogClientImpl(restClient);
    }

    @Test
    @DisplayName("should successfully retrieve product from catalog service")
    void shouldReturnProductWhenFound() {
        // Arrange
        String responseJson = """
                {
                    "id": "%s",
                    "price": 49.99,
                    "currency": "USD",
                    "status": "ACTIVE"
                }
                """.formatted(productId);

        mockServer.expect(requestTo("/api/v1/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        // Act
        CatalogProductResponse response = catalogClient.getProduct(productId);

        // Assert
        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(productId);
        assertThat(response.price()).isEqualByComparingTo(new BigDecimal("49.99"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("should throw ProductNotFoundException when catalog returns 404 Not Found")
    void shouldThrowProductNotFoundWhenCatalogReturns404() {
        // Arrange
        mockServer.expect(requestTo("/api/v1/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // Act & Assert
        assertThatThrownBy(() -> catalogClient.getProduct(productId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(productId.toString());

        mockServer.verify();
    }

    @Test
    @DisplayName("should throw CatalogUnavailableException when catalog returns 500 Internal Server Error")
    void shouldThrowCatalogUnavailableWhenCatalogReturns500() {
        // Arrange
        mockServer.expect(requestTo("/api/v1/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        // Act & Assert
        assertThatThrownBy(() -> catalogClient.getProduct(productId))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasMessage("Catalog service is temporarily unavailable");

        mockServer.verify();
    }

    @Test
    @DisplayName("should throw CatalogUnavailableException when catalog returns 503 Service Unavailable")
    void shouldThrowCatalogUnavailableWhenCatalogReturns503() {
        // Arrange
        mockServer.expect(requestTo("/api/v1/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // Act & Assert
        assertThatThrownBy(() -> catalogClient.getProduct(productId))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasMessage("Catalog service is temporarily unavailable");

        mockServer.verify();
    }

    @Test
    @DisplayName("should throw CatalogUnavailableException when ResourceAccessException occurs")
    void shouldThrowCatalogUnavailableWhenNetworkErrorOccurs() {
        // Arrange
        RestClient mockRestClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        when(mockRestClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(String.class), any(Object[].class))).thenThrow(
                new ResourceAccessException("I/O error", new IOException("Connection timed out"))
        );

        CatalogClientImpl clientWithMock = new CatalogClientImpl(mockRestClient);

        // Act & Assert
        assertThatThrownBy(() -> clientWithMock.getProduct(productId))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasMessage("Catalog service is temporarily unavailable");
    }
}
