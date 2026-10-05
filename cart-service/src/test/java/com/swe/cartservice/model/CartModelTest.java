package com.swe.cartservice.model;

import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.dto.CatalogProductResponse;
import com.swe.cartservice.dto.ProductStatus;
import com.swe.cartservice.dto.UpdateCartItemRequest;
import com.swe.cartservice.exception.CartItemNotFoundException;
import com.swe.cartservice.exception.CartNotFoundException;
import com.swe.cartservice.exception.CatalogUnavailableException;
import com.swe.cartservice.exception.ProductNotAvailableException;
import com.swe.cartservice.exception.ProductNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartModelTest {

    @Test
    @DisplayName("Cart and CartItem records should hold values and support equality")
    void testCartAndCartItemRecords() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        CartItem item1 = new CartItem(productId, 3, new BigDecimal("19.99"));
        CartItem item2 = new CartItem(productId, 3, new BigDecimal("19.99"));

        assertThat(item1).isEqualTo(item2);
        assertThat(item1.hashCode()).isEqualTo(item2.hashCode());
        assertThat(item1.productId()).isEqualTo(productId);
        assertThat(item1.quantity()).isEqualTo(3);
        assertThat(item1.price()).isEqualTo(new BigDecimal("19.99"));

        Cart cart1 = new Cart(customerId, List.of(item1), now);
        Cart cart2 = new Cart(customerId, List.of(item2), now);

        assertThat(cart1).isEqualTo(cart2);
        assertThat(cart1.customerId()).isEqualTo(customerId);
        assertThat(cart1.items()).containsExactly(item1);
        assertThat(cart1.updatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("DTOs should correctly hold fields and support equality")
    void testDtoRecords() {
        UUID productId = UUID.randomUUID();
        AddCartItemRequest addRequest1 = new AddCartItemRequest(productId, 2);
        AddCartItemRequest addRequest2 = new AddCartItemRequest(productId, 2);

        assertThat(addRequest1).isEqualTo(addRequest2);
        assertThat(addRequest1.hashCode()).isEqualTo(addRequest2.hashCode());
        assertThat(addRequest1.productId()).isEqualTo(productId);
        assertThat(addRequest1.quantity()).isEqualTo(2);

        UpdateCartItemRequest updateRequest1 = new UpdateCartItemRequest(5);
        UpdateCartItemRequest updateRequest2 = new UpdateCartItemRequest(5);

        assertThat(updateRequest1).isEqualTo(updateRequest2);
        assertThat(updateRequest1.hashCode()).isEqualTo(updateRequest2.hashCode());
        assertThat(updateRequest1.quantity()).isEqualTo(5);

        CatalogProductResponse catalogResponse1 = new CatalogProductResponse(
                productId, new BigDecimal("99.99"), "USD", ProductStatus.ACTIVE
        );
        CatalogProductResponse catalogResponse2 = new CatalogProductResponse(
                productId, new BigDecimal("99.99"), "USD", ProductStatus.ACTIVE
        );

        assertThat(catalogResponse1).isEqualTo(catalogResponse2);
        assertThat(catalogResponse1.hashCode()).isEqualTo(catalogResponse2.hashCode());
        assertThat(catalogResponse1.id()).isEqualTo(productId);
        assertThat(catalogResponse1.price()).isEqualTo(new BigDecimal("99.99"));
        assertThat(catalogResponse1.currency()).isEqualTo("USD");
        assertThat(catalogResponse1.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("ProductStatus enum values should be defined")
    void testProductStatusEnum() {
        assertThat(ProductStatus.valueOf("ACTIVE")).isEqualTo(ProductStatus.ACTIVE);
        assertThat(ProductStatus.valueOf("RETIRED")).isEqualTo(ProductStatus.RETIRED);
        assertThat(ProductStatus.values()).containsExactly(ProductStatus.ACTIVE, ProductStatus.RETIRED);
    }

    @Test
    @DisplayName("Exceptions should have descriptive messages")
    void testExceptions() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CartNotFoundException notFoundEx = new CartNotFoundException(customerId);
        assertThat(notFoundEx.getMessage()).isEqualTo("Cart not found for customer: " + customerId);

        CartItemNotFoundException itemNotFoundEx = new CartItemNotFoundException(productId);
        assertThat(itemNotFoundEx.getMessage()).isEqualTo("Product not found in cart: " + productId);

        ProductNotFoundException productNotFoundEx = new ProductNotFoundException(productId);
        assertThat(productNotFoundEx.getMessage()).isEqualTo("Product not found: " + productId);

        ProductNotAvailableException productNotAvailableEx = new ProductNotAvailableException(productId);
        assertThat(productNotAvailableEx.getMessage()).isEqualTo("Product is not available: " + productId);

        CatalogUnavailableException catalogUnavailableEx1 = new CatalogUnavailableException();
        assertThat(catalogUnavailableEx1.getMessage()).isEqualTo("Catalog service is temporarily unavailable");

        RuntimeException cause = new RuntimeException("Connection refused");
        CatalogUnavailableException catalogUnavailableEx2 = new CatalogUnavailableException(cause);
        assertThat(catalogUnavailableEx2.getMessage()).isEqualTo("Catalog service is temporarily unavailable");
        assertThat(catalogUnavailableEx2.getCause()).isEqualTo(cause);
    }
}
