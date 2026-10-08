package com.swe.cartservice.controller;

import com.swe.cartservice.dto.AddCartItemRequest;
import com.swe.cartservice.dto.CheckoutResponse;
import com.swe.cartservice.dto.UpdateCartItemRequest;
import com.swe.cartservice.exception.CartItemNotFoundException;
import com.swe.cartservice.exception.CartNotFoundException;
import com.swe.cartservice.exception.CatalogUnavailableException;
import com.swe.cartservice.exception.EmptyCartException;
import com.swe.cartservice.exception.GlobalExceptionHandler;
import com.swe.cartservice.exception.OrderServiceUnavailableException;
import com.swe.cartservice.exception.ProductNotAvailableException;
import com.swe.cartservice.exception.ProductNotFoundException;
import com.swe.cartservice.model.Cart;
import com.swe.cartservice.model.CartItem;
import com.swe.cartservice.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import(GlobalExceptionHandler.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "/api/v1/carts";

    @Nested
    @DisplayName("GET /api/v1/carts/{customerId}")
    class GetCartEndpointTests {

        @Test
        @DisplayName("should return 200 OK with cart payload")
        void shouldReturn200WithCart() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            Cart cart = new Cart(
                    customerId,
                    List.of(new CartItem(productId, 2, new BigDecimal("29.99"))),
                    OffsetDateTime.now()
            );
            when(cartService.getCart(customerId)).thenReturn(cart);

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/{customerId}", customerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                    .andExpect(jsonPath("$.items[0].quantity").value(2))
                    .andExpect(jsonPath("$.items[0].price").value(29.99));

            verify(cartService).getCart(customerId);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/carts/{customerId}/items")
    class AddItemEndpointTests {

        @Test
        @DisplayName("should return 200 OK when item is successfully added")
        void shouldReturn200WhenItemAdded() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            AddCartItemRequest request = new AddCartItemRequest(productId, 3);
            Cart updatedCart = new Cart(
                    customerId,
                    List.of(new CartItem(productId, 3, new BigDecimal("19.99"))),
                    OffsetDateTime.now()
            );
            when(cartService.addItem(eq(customerId), any(AddCartItemRequest.class))).thenReturn(updatedCart);

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/items", customerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                    .andExpect(jsonPath("$.items[0].quantity").value(3));

            verify(cartService).addItem(eq(customerId), any(AddCartItemRequest.class));
        }

        @Test
        @DisplayName("should return 400 Bad Request when request body has null productId")
        void shouldReturn400WhenProductIdNull() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            String invalidJson = """
                    {
                        "productId": null,
                        "quantity": 2
                    }
                    """;

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/items", customerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.productId").exists());

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("should return 400 Bad Request when quantity is zero or negative")
        void shouldReturn400WhenQuantityNotPositive() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            AddCartItemRequest request = new AddCartItemRequest(UUID.randomUUID(), 0);

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/items", customerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.quantity").exists());

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("should return 400 Bad Request on malformed JSON")
        void shouldReturn400OnMalformedJson() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/items", customerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid-json}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Malformed JSON request"));

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("should return 404 Not Found when product is not found in catalog")
        void shouldReturn404WhenProductNotFoundInCatalog() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            AddCartItemRequest request = new AddCartItemRequest(productId, 2);
            when(cartService.addItem(eq(customerId), any(AddCartItemRequest.class)))
                    .thenThrow(new ProductNotFoundException(productId));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/items", customerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Product not found: " + productId));
        }

        @Test
        @DisplayName("should return 409 Conflict when product is not active")
        void shouldReturn409WhenProductNotAvailable() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            AddCartItemRequest request = new AddCartItemRequest(productId, 2);
            when(cartService.addItem(eq(customerId), any(AddCartItemRequest.class)))
                    .thenThrow(new ProductNotAvailableException(productId));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/items", customerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.message").value("Product is not available: " + productId));
        }

        @Test
        @DisplayName("should return 503 Service Unavailable when catalog service is unavailable")
        void shouldReturn503WhenCatalogUnavailable() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            AddCartItemRequest request = new AddCartItemRequest(productId, 2);
            when(cartService.addItem(eq(customerId), any(AddCartItemRequest.class)))
                    .thenThrow(new CatalogUnavailableException());

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/items", customerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.error").value("Service Unavailable"))
                    .andExpect(jsonPath("$.message").value("Catalog service is temporarily unavailable"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/carts/{customerId}/items/{productId}")
    class UpdateItemQuantityEndpointTests {

        @Test
        @DisplayName("should return 200 OK when item quantity is updated")
        void shouldReturn200WhenQuantityUpdated() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UpdateCartItemRequest request = new UpdateCartItemRequest(5);
            Cart updatedCart = new Cart(
                    customerId,
                    List.of(new CartItem(productId, 5, new BigDecimal("10.00"))),
                    OffsetDateTime.now()
            );
            when(cartService.updateItemQuantity(customerId, productId, 5)).thenReturn(updatedCart);

            // Act & Assert
            mockMvc.perform(patch(BASE_URL + "/{customerId}/items/{productId}", customerId, productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.items[0].quantity").value(5));

            verify(cartService).updateItemQuantity(customerId, productId, 5);
        }

        @Test
        @DisplayName("should return 400 Bad Request when update quantity is non-positive")
        void shouldReturn400WhenQuantityZero() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UpdateCartItemRequest request = new UpdateCartItemRequest(-1);

            // Act & Assert
            mockMvc.perform(patch(BASE_URL + "/{customerId}/items/{productId}", customerId, productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.quantity").exists());

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("should return 404 Not Found when cart does not exist")
        void shouldReturn404WhenCartNotFound() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UpdateCartItemRequest request = new UpdateCartItemRequest(2);
            when(cartService.updateItemQuantity(customerId, productId, 2))
                    .thenThrow(new CartNotFoundException(customerId));

            // Act & Assert
            mockMvc.perform(patch(BASE_URL + "/{customerId}/items/{productId}", customerId, productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Cart not found for customer: " + customerId));
        }

        @Test
        @DisplayName("should return 404 Not Found when product is not in cart")
        void shouldReturn404WhenProductNotFoundInCart() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UpdateCartItemRequest request = new UpdateCartItemRequest(2);
            when(cartService.updateItemQuantity(customerId, productId, 2))
                    .thenThrow(new CartItemNotFoundException(productId));

            // Act & Assert
            mockMvc.perform(patch(BASE_URL + "/{customerId}/items/{productId}", customerId, productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product not found in cart: " + productId));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/carts/{customerId}/items/{productId}")
    class RemoveItemEndpointTests {

        @Test
        @DisplayName("should return 200 OK when item is removed from cart")
        void shouldReturn200WhenItemRemoved() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            Cart updatedCart = new Cart(customerId, List.of(), OffsetDateTime.now());
            when(cartService.removeItem(customerId, productId)).thenReturn(updatedCart);

            // Act & Assert
            mockMvc.perform(delete(BASE_URL + "/{customerId}/items/{productId}", customerId, productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(0));

            verify(cartService).removeItem(customerId, productId);
        }

        @Test
        @DisplayName("should return 404 Not Found when removing non-existent item")
        void shouldReturn404WhenItemNotFound() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            when(cartService.removeItem(customerId, productId))
                    .thenThrow(new CartItemNotFoundException(productId));

            // Act & Assert
            mockMvc.perform(delete(BASE_URL + "/{customerId}/items/{productId}", customerId, productId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product not found in cart: " + productId));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/carts/{customerId}")
    class ClearCartEndpointTests {

        @Test
        @DisplayName("should return 204 No Content when cart is cleared")
        void shouldReturn204WhenCartCleared() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            doNothing().when(cartService).clearCart(customerId);

            // Act & Assert
            mockMvc.perform(delete(BASE_URL + "/{customerId}", customerId))
                    .andExpect(status().isNoContent());

            verify(cartService).clearCart(customerId);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/carts/{customerId}/checkout")
    class CheckoutEndpointTests {

        @Test
        @DisplayName("should return 200 OK and checkout response when checkout is successful")
        void shouldReturn200WhenCheckoutSuccessful() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            CheckoutResponse checkoutResponse = new CheckoutResponse(orderId, "PENDING");

            when(cartService.checkout(customerId, idempotencyKey)).thenReturn(checkoutResponse);

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/checkout", customerId)
                            .header("Idempotency-Key", idempotencyKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            verify(cartService).checkout(customerId, idempotencyKey);
        }

        @Test
        @DisplayName("should return 400 Bad Request when Idempotency-Key header is missing")
        void shouldReturn400WhenIdempotencyKeyMissing() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/checkout", customerId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Missing required header: Idempotency-Key"));

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("should return 409 Conflict when cart is empty")
        void shouldReturn409WhenCartIsEmpty() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            when(cartService.checkout(customerId, idempotencyKey)).thenThrow(new EmptyCartException(customerId));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/checkout", customerId)
                            .header("Idempotency-Key", idempotencyKey))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.message").value("Cannot checkout an empty cart for customer: " + customerId));
        }

        @Test
        @DisplayName("should return 404 Not Found when cart does not exist")
        void shouldReturn404WhenCartNotFound() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            when(cartService.checkout(customerId, idempotencyKey)).thenThrow(new CartNotFoundException(customerId));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/checkout", customerId)
                            .header("Idempotency-Key", idempotencyKey))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Cart not found for customer: " + customerId));
        }

        @Test
        @DisplayName("should return 409 Conflict when product is not active")
        void shouldReturn409WhenProductNotActive() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            when(cartService.checkout(customerId, idempotencyKey)).thenThrow(new ProductNotAvailableException(productId));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/checkout", customerId)
                            .header("Idempotency-Key", idempotencyKey))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.message").value("Product is not available: " + productId));
        }

        @Test
        @DisplayName("should return 404 Not Found when product is not found in catalog")
        void shouldReturn404WhenProductNotFound() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            when(cartService.checkout(customerId, idempotencyKey)).thenThrow(new ProductNotFoundException(productId));

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/checkout", customerId)
                            .header("Idempotency-Key", idempotencyKey))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Product not found: " + productId));
        }

        @Test
        @DisplayName("should return 503 Service Unavailable when catalog service is unavailable")
        void shouldReturn503WhenCatalogUnavailable() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            when(cartService.checkout(customerId, idempotencyKey)).thenThrow(new CatalogUnavailableException());

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/checkout", customerId)
                            .header("Idempotency-Key", idempotencyKey))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.error").value("Service Unavailable"))
                    .andExpect(jsonPath("$.message").value("Catalog service is temporarily unavailable"));
        }

        @Test
        @DisplayName("should return 503 Service Unavailable when order service is unavailable")
        void shouldReturn503WhenOrderServiceUnavailable() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            String idempotencyKey = UUID.randomUUID().toString();
            when(cartService.checkout(customerId, idempotencyKey)).thenThrow(new OrderServiceUnavailableException());

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{customerId}/checkout", customerId)
                            .header("Idempotency-Key", idempotencyKey))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.error").value("Service Unavailable"))
                    .andExpect(jsonPath("$.message").value("Orders service is temporarily unavailable"));
        }
    }

    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("should return 503 Service Unavailable when Redis DataAccessException occurs")
        void shouldReturn503OnRedisFailure() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            when(cartService.getCart(customerId)).thenThrow(new QueryTimeoutException("Redis connection timed out"));

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/{customerId}", customerId))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.error").value("Service Unavailable"))
                    .andExpect(jsonPath("$.message").value("Cart service storage is temporarily unavailable"));
        }

        @Test
        @DisplayName("should return 500 Internal Server Error on unexpected exception")
        void shouldReturn500OnUnexpectedException() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            when(cartService.getCart(customerId)).thenThrow(new NullPointerException("Unexpected NPE"));

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/{customerId}", customerId))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.error").value("Internal Server Error"))
                    .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
        }
    }
}
