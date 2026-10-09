package com.swe.ordersservice.service;

import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.event.OrderCreatedEvent;
import com.swe.ordersservice.metrics.AfterCommitExecutor;
import com.swe.ordersservice.metrics.OrderMetrics;
import com.swe.ordersservice.outbox.OutboxEvent;
import com.swe.ordersservice.outbox.OutboxEventFactory;
import com.swe.ordersservice.outbox.OutboxEventRepository;
import com.swe.ordersservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCreationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventFactory outboxEventFactory;

    @Mock
    private OrderMetrics orderMetrics;

    @Mock
    private AfterCommitExecutor afterCommitExecutor;

    @InjectMocks
    private OrderCreationService orderCreationService;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(afterCommitExecutor).execute(any());
    }

    @Test
    @DisplayName("should create and persist order with items, idempotency key, and outbox event using MDC correlationId")
    void shouldCreateOrderSuccessfullyWithMdcCorrelationId() {
        // Arrange
        String correlationId = "test-correlation-" + UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        try {
            UUID customerId = UUID.randomUUID();
            UUID product1Id = UUID.randomUUID();
            UUID product2Id = UUID.randomUUID();

            List<OrderItemRequest> itemRequests = List.of(
                    new OrderItemRequest(product1Id, 2),
                    new OrderItemRequest(product2Id, 5)
            );
            OrderRequest request = new OrderRequest(customerId, itemRequests);

            UUID generatedOrderId = UUID.randomUUID();
            when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
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
                    .correlationId(correlationId)
                    .payload("{\"orderId\":\"" + generatedOrderId + "\"}")
                    .createdAt(OffsetDateTime.now())
                    .retryCount(0)
                    .build();

            when(outboxEventFactory.create(any(OrderCreatedEvent.class), eq(correlationId))).thenReturn(mockOutboxEvent);

            // Act
            OrderResponse response = orderCreationService.create(idempotencyKey, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(generatedOrderId);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

            // Verify order persistence
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).saveAndFlush(orderCaptor.capture());

            Order capturedOrder = orderCaptor.getValue();
            assertThat(capturedOrder.getCustomerId()).isEqualTo(customerId);
            assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(capturedOrder.getIdempotencyKey()).isEqualTo(idempotencyKey);
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
            verify(outboxEventFactory).create(eventCaptor.capture(), eq(correlationId));

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
            verify(afterCommitExecutor).execute(any());
            verify(orderMetrics).orderCreated();

            // Verify MDC cleanup
            assertThat(MDC.get("orderId")).isNull();
            assertThat(MDC.get("eventId")).isNull();
        } finally {
            MDC.remove("correlationId");
        }
    }

    @Test
    @DisplayName("should generate new correlationId when MDC correlationId is blank")
    void shouldGenerateNewCorrelationIdWhenMdcIsBlank() {
        // Arrange
        MDC.remove("correlationId");
        String idempotencyKey = UUID.randomUUID().toString();

        UUID customerId = UUID.randomUUID();
        OrderRequest request = new OrderRequest(
                customerId,
                List.of(new OrderItemRequest(UUID.randomUUID(), 1))
        );

        UUID generatedOrderId = UUID.randomUUID();
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order orderToSave = invocation.getArgument(0);
            orderToSave.setId(generatedOrderId);
            return orderToSave;
        });

        OutboxEvent mockOutboxEvent = OutboxEvent.builder().build();
        when(outboxEventFactory.create(any(OrderCreatedEvent.class), anyString())).thenReturn(mockOutboxEvent);

        // Act
        OrderResponse response = orderCreationService.create(idempotencyKey, request);

        // Assert
        assertThat(response).isNotNull();

        ArgumentCaptor<String> correlationIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxEventFactory).create(any(OrderCreatedEvent.class), correlationIdCaptor.capture());

        String passedCorrelationId = correlationIdCaptor.getValue();
        assertThat(passedCorrelationId).isNotBlank();
        assertThat(UUID.fromString(passedCorrelationId)).isNotNull();
        verify(outboxEventRepository).save(mockOutboxEvent);
    }

    @Test
    @DisplayName("should propagate DataIntegrityViolationException when repository saveAndFlush fails")
    void shouldPropagateDataIntegrityViolationExceptionWhenSaveAndFlushFails() {
        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(UUID.randomUUID(), 1))
        );

        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        // Act & Assert
        assertThatThrownBy(() -> orderCreationService.create(idempotencyKey, request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("duplicate key value violates unique constraint");

        verify(orderRepository).saveAndFlush(any(Order.class));
        verifyNoInteractions(outboxEventFactory);
        verifyNoInteractions(outboxEventRepository);
        verifyNoInteractions(orderMetrics);
    }

    @Test
    @DisplayName("should propagate exception when outbox event creation fails")
    void shouldPropagateExceptionWhenOutboxFactoryFails() {
        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(UUID.randomUUID(), 1))
        );

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order orderToSave = invocation.getArgument(0);
            orderToSave.setId(UUID.randomUUID());
            return orderToSave;
        });

        when(outboxEventFactory.create(any(OrderCreatedEvent.class), anyString()))
                .thenThrow(new IllegalStateException("Failed to serialize OrderCreatedEvent"));

        // Act & Assert
        assertThatThrownBy(() -> orderCreationService.create(idempotencyKey, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize OrderCreatedEvent");

        verify(orderRepository).saveAndFlush(any(Order.class));
        verify(outboxEventFactory).create(any(OrderCreatedEvent.class), anyString());
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    @DisplayName("should propagate exception when outbox repository save fails")
    void shouldPropagateExceptionWhenOutboxRepositoryFails() {
        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(UUID.randomUUID(), 1))
        );

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order orderToSave = invocation.getArgument(0);
            orderToSave.setId(UUID.randomUUID());
            return orderToSave;
        });

        OutboxEvent mockOutboxEvent = OutboxEvent.builder().build();
        when(outboxEventFactory.create(any(OrderCreatedEvent.class), anyString())).thenReturn(mockOutboxEvent);
        when(outboxEventRepository.save(mockOutboxEvent))
                .thenThrow(new RuntimeException("Outbox persistence failure"));

        // Act & Assert
        assertThatThrownBy(() -> orderCreationService.create(idempotencyKey, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Outbox persistence failure");

        verify(orderRepository).saveAndFlush(any(Order.class));
        verify(outboxEventFactory).create(any(OrderCreatedEvent.class), anyString());
        verify(outboxEventRepository).save(mockOutboxEvent);
    }
}
