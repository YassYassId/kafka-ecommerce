package com.swe.ordersservice.service;

import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        @Test
        @DisplayName("should successfully create and persist order with items")
        void shouldCreateOrderSuccessfully() {
            // Arrange
            UUID customerId = UUID.randomUUID();
            UUID product1Id = UUID.randomUUID();
            UUID product2Id = UUID.randomUUID();

            List<OrderItemRequest> itemRequests = List.of(
                    new OrderItemRequest(product1Id, 2),
                    new OrderItemRequest(product2Id, 5)
            );
            OrderRequest request = new OrderRequest(customerId, itemRequests);

            UUID generatedOrderId = UUID.randomUUID();
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order orderToSave = invocation.getArgument(0);
                // Simulate JPA assigning generated ID on persist
                orderToSave.setId(generatedOrderId);
                return orderToSave;
            });

            // Act
            OrderResponse response = orderService.createOrder(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(generatedOrderId);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

            // Verify the entity passed to repository
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());

            Order capturedOrder = orderCaptor.getValue();
            assertThat(capturedOrder.getCustomerId()).isEqualTo(customerId);
            assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(capturedOrder.getItems()).hasSize(2);

            // Verify relationship integrity on items
            assertThat(capturedOrder.getItems())
                    .extracting("productId")
                    .containsExactlyInAnyOrder(product1Id, product2Id);

            assertThat(capturedOrder.getItems())
                    .extracting("quantity")
                    .containsExactlyInAnyOrder(2, 5);

            capturedOrder.getItems().forEach(item ->
                    assertThat(item.getOrder()).isSameAs(capturedOrder)
            );
        }

        @Test
        @DisplayName("should propagate exception when repository save fails")
        void shouldPropagateExceptionWhenRepositoryFails() {
            // Arrange
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            when(orderRepository.save(any(Order.class)))
                    .thenThrow(new RuntimeException("Database connectivity failure"));

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database connectivity failure");

            verify(orderRepository).save(any(Order.class));
        }
    }
}
