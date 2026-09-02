package com.swe.ordersservice.service;

import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.event.OrderCreatedEvent;
import com.swe.ordersservice.exception.OrderNotFoundException;
import com.swe.ordersservice.outbox.OutboxEvent;
import com.swe.ordersservice.outbox.OutboxEventFactory;
import com.swe.ordersservice.outbox.OutboxEventRepository;
import com.swe.ordersservice.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventFactory outboxEventFactory;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        @Test
        @DisplayName("should successfully create and persist order with items and outbox event")
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
                orderToSave.setId(generatedOrderId);
                return orderToSave;
            });

            OutboxEvent mockOutboxEvent = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType("Order")
                    .aggregateId(generatedOrderId)
                    .eventType("OrderCreated")
                    .eventVersion(1)
                    .payload("{\"orderId\":\"" + generatedOrderId + "\"}")
                    .createdAt(OffsetDateTime.now())
                    .retryCount(0)
                    .build();

            when(outboxEventFactory.create(any(OrderCreatedEvent.class))).thenReturn(mockOutboxEvent);

            // Act
            OrderResponse response = orderService.createOrder(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(generatedOrderId);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

            // Verify order persistence
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());

            Order capturedOrder = orderCaptor.getValue();
            assertThat(capturedOrder.getCustomerId()).isEqualTo(customerId);
            assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(capturedOrder.getItems()).hasSize(2);

            assertThat(capturedOrder.getItems())
                    .extracting("productId")
                    .containsExactlyInAnyOrder(product1Id, product2Id);

            assertThat(capturedOrder.getItems())
                    .extracting("quantity")
                    .containsExactlyInAnyOrder(2, 5);

            capturedOrder.getItems().forEach(item ->
                    assertThat(item.getOrder()).isSameAs(capturedOrder)
            );

            // Verify outbox event creation and persistence
            ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(outboxEventFactory).create(eventCaptor.capture());

            OrderCreatedEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.eventId()).isNotNull();
            assertThat(capturedEvent.orderId()).isEqualTo(generatedOrderId);
            assertThat(capturedEvent.customerId()).isEqualTo(customerId);
            assertThat(capturedEvent.version()).isEqualTo(1);
            assertThat(capturedEvent.occurredAt()).isNotNull();
            assertThat(capturedEvent.items()).hasSize(2);
            assertThat(capturedEvent.items())
                    .extracting("productId")
                    .containsExactlyInAnyOrder(product1Id, product2Id);

            verify(outboxEventRepository).save(mockOutboxEvent);
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
            verifyNoInteractions(outboxEventFactory);
            verifyNoInteractions(outboxEventRepository);
        }

        @Test
        @DisplayName("should propagate exception when outbox event creation fails")
        void shouldPropagateExceptionWhenOutboxFactoryFails() {
            // Arrange
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order orderToSave = invocation.getArgument(0);
                orderToSave.setId(UUID.randomUUID());
                return orderToSave;
            });

            when(outboxEventFactory.create(any(OrderCreatedEvent.class)))
                    .thenThrow(new IllegalStateException("Failed to serialize OrderCreatedEvent"));

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Failed to serialize OrderCreatedEvent");

            verify(orderRepository).save(any(Order.class));
            verify(outboxEventFactory).create(any(OrderCreatedEvent.class));
            verifyNoInteractions(outboxEventRepository);
        }

        @Test
        @DisplayName("should propagate exception when outbox repository save fails")
        void shouldPropagateExceptionWhenOutboxRepositoryFails() {
            // Arrange
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order orderToSave = invocation.getArgument(0);
                orderToSave.setId(UUID.randomUUID());
                return orderToSave;
            });

            OutboxEvent mockOutboxEvent = OutboxEvent.builder().build();
            when(outboxEventFactory.create(any(OrderCreatedEvent.class))).thenReturn(mockOutboxEvent);
            when(outboxEventRepository.save(mockOutboxEvent))
                    .thenThrow(new RuntimeException("Outbox persistence failure"));

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Outbox persistence failure");

            verify(orderRepository).save(any(Order.class));
            verify(outboxEventFactory).create(any(OrderCreatedEvent.class));
            verify(outboxEventRepository).save(mockOutboxEvent);
        }
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrderTests {

        @Test
        @DisplayName("should return order response when order exists")
        void shouldReturnOrderResponseWhenOrderExists() {
            // Arrange
            UUID orderId = UUID.randomUUID();
            Order existingOrder = Order.builder()
                    .id(orderId)
                    .customerId(UUID.randomUUID())
                    .status(OrderStatus.PENDING)
                    .build();

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(existingOrder));

            // Act
            OrderResponse response = orderService.getOrder(orderId);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(orderId);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
            verify(orderRepository).findById(orderId);
        }

        @Test
        @DisplayName("should throw OrderNotFoundException when order does not exist")
        void shouldThrowOrderNotFoundExceptionWhenOrderDoesNotExist() {
            // Arrange
            UUID nonExistentOrderId = UUID.randomUUID();
            when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.getOrder(nonExistentOrderId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessage("Order not found with ID: " + nonExistentOrderId);

            verify(orderRepository).findById(nonExistentOrderId);
        }
    }
}
