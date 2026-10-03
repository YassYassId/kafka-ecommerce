package com.swe.cartservice.model;

import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.dto.UpdateCartItemRequest;
import com.swe.cartservice.exception.CartItemNotFoundException;
import com.swe.cartservice.exception.CartNotFoundException;
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
        AddCartItemRequest addRequest1 = new AddCartItemRequest(productId, 2, new BigDecimal("49.99"));
        AddCartItemRequest addRequest2 = new AddCartItemRequest(productId, 2, new BigDecimal("49.99"));

        assertThat(addRequest1).isEqualTo(addRequest2);
        assertThat(addRequest1.productId()).isEqualTo(productId);
        assertThat(addRequest1.quantity()).isEqualTo(2);
        assertThat(addRequest1.price()).isEqualTo(new BigDecimal("49.99"));

        UpdateCartItemRequest updateRequest1 = new UpdateCartItemRequest(5);
        UpdateCartItemRequest updateRequest2 = new UpdateCartItemRequest(5);

        assertThat(updateRequest1).isEqualTo(updateRequest2);
        assertThat(updateRequest1.quantity()).isEqualTo(5);
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
    }
}
