package com.swe.ordersservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
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
                    .andExpect(status().isBadRequest());

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
                    .andExpect(status().isBadRequest());

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
                    .andExpect(status().isBadRequest());

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
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }
    }
}
