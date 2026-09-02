package com.swe.ordersservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.exception.GlobalExceptionHandler;
import com.swe.ordersservice.exception.OrderNotFoundException;
import com.swe.ordersservice.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private OrderService orderService;

    private static final String ORDERS_URL = "/api/v1/orders";

    @Nested
    @DisplayName("POST /api/v1/orders")
    class CreateOrderApiTests {

        @Test
        @DisplayName("should return 201 Created and order response when request is valid")
        void createOrder_WhenValidRequest_ShouldReturn201Created() throws Exception {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID generatedOrderId = UUID.randomUUID();

            OrderRequest request = new OrderRequest(
                    customerId,
                    List.of(new OrderItemRequest(productId, 3))
            );

            OrderResponse mockResponse = new OrderResponse(generatedOrderId, OrderStatus.PENDING);
            when(orderService.createOrder(any(OrderRequest.class))).thenReturn(mockResponse);

            // Act & Assert
            mockMvc.perform(post(ORDERS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").value(generatedOrderId.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("should return 400 Bad Request when customerId is null")
        void createOrder_WhenCustomerIdIsNull_ShouldReturn400BadRequest() throws Exception {
            // Arrange
            OrderRequest request = new OrderRequest(
                    null,
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            // Act & Assert
            mockMvc.perform(post(ORDERS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("customerId"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("Customer ID is required"));

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("should return 400 Bad Request when items list is empty")
        void createOrder_WhenItemsListIsEmpty_ShouldReturn400BadRequest() throws Exception {
            // Arrange
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    Collections.emptyList()
            );

            // Act & Assert
            mockMvc.perform(post(ORDERS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("items"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("Order must contain at least one item"));

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("should return 400 Bad Request when item productId is null")
        void createOrder_WhenItemProductIdIsNull_ShouldReturn400BadRequest() throws Exception {
            // Arrange
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(null, 2))
            );

            // Act & Assert
            mockMvc.perform(post(ORDERS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("items[0].productId"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("Product ID is required"));

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("should return 400 Bad Request when item quantity is zero or negative")
        void createOrder_WhenItemQuantityIsNonPositive_ShouldReturn400BadRequest() throws Exception {
            // Arrange
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 0))
            );

            // Act & Assert
            mockMvc.perform(post(ORDERS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("items[0].quantity"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("Quantity must be greater than zero"));

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("should return 400 Bad Request when payload is malformed JSON")
        void createOrder_WhenMalformedJson_ShouldReturn400BadRequest() throws Exception {
            // Act & Assert
            mockMvc.perform(post(ORDERS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"customerId\": \"not-a-valid-uuid\", \"items\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Malformed JSON request or invalid data format"));

            verifyNoInteractions(orderService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{orderId}")
    class GetOrderApiTests {

        @Test
        @DisplayName("should return 200 OK and order response when order exists")
        void getOrder_WhenOrderExists_ShouldReturn200Ok() throws Exception {
            // Arrange
            UUID orderId = UUID.randomUUID();
            OrderResponse mockResponse = new OrderResponse(orderId, OrderStatus.PENDING);

            when(orderService.getOrder(orderId)).thenReturn(mockResponse);

            // Act & Assert
            mockMvc.perform(get(ORDERS_URL + "/{orderId}", orderId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            verify(orderService).getOrder(orderId);
        }

        @Test
        @DisplayName("should return 404 Not Found and structured error response when order does not exist")
        void getOrder_WhenOrderDoesNotExist_ShouldReturn404NotFound() throws Exception {
            // Arrange
            UUID nonExistentOrderId = UUID.randomUUID();
            when(orderService.getOrder(nonExistentOrderId))
                    .thenThrow(new OrderNotFoundException(nonExistentOrderId));

            // Act & Assert
            mockMvc.perform(get(ORDERS_URL + "/{orderId}", nonExistentOrderId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Order not found with ID: " + nonExistentOrderId))
                    .andExpect(jsonPath("$.path").value(ORDERS_URL + "/" + nonExistentOrderId))
                    .andExpect(jsonPath("$.timestamp").exists());

            verify(orderService).getOrder(nonExistentOrderId);
        }

        @Test
        @DisplayName("should return 400 Bad Request when orderId is not a valid UUID")
        void getOrder_WhenOrderIdIsInvalidUUID_ShouldReturn400BadRequest() throws Exception {
            // Act & Assert
            mockMvc.perform(get(ORDERS_URL + "/{orderId}", "not-a-valid-uuid")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.path").value(ORDERS_URL + "/not-a-valid-uuid"))
                    .andExpect(jsonPath("$.timestamp").exists());

            verifyNoInteractions(orderService);
        }
    }
}
