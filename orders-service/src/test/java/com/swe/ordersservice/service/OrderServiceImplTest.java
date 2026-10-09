package com.swe.ordersservice.service;

import com.swe.ordersservice.dto.OrderItemRequest;
import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.entity.ProcessedEvent;
import com.swe.ordersservice.event.InventoryRejectedEvent;
import com.swe.ordersservice.event.InventoryReservedEvent;
import com.swe.ordersservice.exception.InvalidIdempotencyKeyException;
import com.swe.ordersservice.exception.OrderNotFoundException;
import com.swe.ordersservice.metrics.AfterCommitExecutor;
import com.swe.ordersservice.metrics.OrderMetrics;
import com.swe.ordersservice.repository.OrderRepository;
import com.swe.ordersservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
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
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OrderMetrics orderMetrics;

    @Mock
    private AfterCommitExecutor afterCommitExecutor;

    @Mock
    private OrderCreationService orderCreationService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(afterCommitExecutor).execute(any());
    }

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        @Test
        @DisplayName("should successfully delegate order creation to OrderCreationService when idempotency key does not exist")
        void shouldCreateOrderSuccessfullyWhenKeyDoesNotExist() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            UUID customerId = UUID.randomUUID();
            UUID generatedOrderId = UUID.randomUUID();

            OrderRequest request = new OrderRequest(
                    customerId,
                    List.of(new OrderItemRequest(UUID.randomUUID(), 2))
            );

            OrderResponse expectedResponse = new OrderResponse(generatedOrderId, OrderStatus.PENDING);

            when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(orderCreationService.create(idempotencyKey, request)).thenReturn(expectedResponse);

            // Act
            OrderResponse response = orderService.createOrder(idempotencyKey, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(generatedOrderId);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

            verify(orderRepository).findByIdempotencyKey(idempotencyKey);
            verify(orderCreationService).create(idempotencyKey, request);
        }

        @Test
        @DisplayName("should return existing order and skip OrderCreationService when idempotency key already exists")
        void shouldReturnExistingOrderWhenIdempotencyKeyExists() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            UUID existingOrderId = UUID.randomUUID();
            Order existingOrder = Order.builder()
                    .id(existingOrderId)
                    .customerId(UUID.randomUUID())
                    .status(OrderStatus.PENDING)
                    .idempotencyKey(idempotencyKey)
                    .build();

            when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));

            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            // Act
            OrderResponse response = orderService.createOrder(idempotencyKey, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(existingOrderId);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

            verify(orderRepository).findByIdempotencyKey(idempotencyKey);
            verifyNoInteractions(orderCreationService);
        }

        @Test
        @DisplayName("should recover and return concurrent order when DataIntegrityViolation occurs and concurrent order exists")
        void shouldReturnConcurrentOrderWhenDataIntegrityViolationOccurs() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            UUID concurrentOrderId = UUID.randomUUID();
            Order concurrentOrder = Order.builder()
                    .id(concurrentOrderId)
                    .customerId(UUID.randomUUID())
                    .status(OrderStatus.PENDING)
                    .idempotencyKey(idempotencyKey)
                    .build();

            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            // Initial check: empty
            // Catch block check: finds concurrent order
            when(orderRepository.findByIdempotencyKey(idempotencyKey))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(concurrentOrder));

            when(orderCreationService.create(idempotencyKey, request))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint violation on idempotency_key"));

            // Act
            OrderResponse response = orderService.createOrder(idempotencyKey, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(concurrentOrderId);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

            verify(orderRepository, times(2)).findByIdempotencyKey(idempotencyKey);
            verify(orderCreationService).create(idempotencyKey, request);
        }

        @Test
        @DisplayName("should rethrow DataIntegrityViolationException when no concurrent order is found")
        void shouldRethrowDataIntegrityViolationExceptionWhenNoConcurrentOrderFound() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            DataIntegrityViolationException databaseException =
                    new DataIntegrityViolationException("Foreign key violation or check constraint");

            when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(orderCreationService.create(idempotencyKey, request)).thenThrow(databaseException);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(idempotencyKey, request))
                    .isSameAs(databaseException);

            verify(orderRepository, times(2)).findByIdempotencyKey(idempotencyKey);
            verify(orderCreationService).create(idempotencyKey, request);
        }

        @Test
        @DisplayName("should throw InvalidIdempotencyKeyException when idempotencyKey is null")
        void shouldThrowInvalidIdempotencyKeyWhenNull() {
            // Arrange
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(null, request))
                    .isInstanceOf(InvalidIdempotencyKeyException.class)
                    .hasMessage("Idempotency-Key must not be blank");

            verifyNoInteractions(orderRepository);
            verifyNoInteractions(orderCreationService);
        }

        @Test
        @DisplayName("should throw InvalidIdempotencyKeyException when idempotencyKey is blank")
        void shouldThrowInvalidIdempotencyKeyWhenBlank() {
            // Arrange
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder("   ", request))
                    .isInstanceOf(InvalidIdempotencyKeyException.class)
                    .hasMessage("Idempotency-Key must not be blank");

            verifyNoInteractions(orderRepository);
            verifyNoInteractions(orderCreationService);
        }

        @Test
        @DisplayName("should propagate unexpected RuntimeException from OrderCreationService")
        void shouldPropagateUnexpectedExceptionFromOrderCreationService() {
            // Arrange
            String idempotencyKey = UUID.randomUUID().toString();
            OrderRequest request = new OrderRequest(
                    UUID.randomUUID(),
                    List.of(new OrderItemRequest(UUID.randomUUID(), 1))
            );

            when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(orderCreationService.create(idempotencyKey, request))
                    .thenThrow(new IllegalStateException("Unexpected server error"));

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(idempotencyKey, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Unexpected server error");

            verify(orderRepository, times(1)).findByIdempotencyKey(idempotencyKey);
            verify(orderCreationService).create(idempotencyKey, request);
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

    @Nested
    @DisplayName("confirmOrder")
    class ConfirmOrderTests {

        @Test
        @DisplayName("should transition order from PENDING to CONFIRMED and record processed event")
        void shouldConfirmOrderSuccessfullyWhenStatusIsPending() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            InventoryReservedEvent event = new InventoryReservedEvent(eventId, orderId, Instant.now(), 1);

            Order pendingOrder = Order.builder()
                    .id(orderId)
                    .customerId(UUID.randomUUID())
                    .status(OrderStatus.PENDING)
                    .build();

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));

            // Act
            orderService.confirmOrder(event);

            // Assert
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
            verify(processedEventRepository).save(captor.capture());

            ProcessedEvent capturedEvent = captor.getValue();
            assertThat(capturedEvent.getEventId()).isEqualTo(eventId);
            assertThat(capturedEvent.getProcessedAt()).isNotNull();
            verify(afterCommitExecutor).execute(any());
            verify(orderMetrics).orderConfirmed();
        }

        @Test
        @DisplayName("should skip processing when event has already been processed (idempotency)")
        void shouldSkipProcessingWhenEventAlreadyProcessed() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            InventoryReservedEvent event = new InventoryReservedEvent(eventId, orderId, Instant.now(), 1);

            when(processedEventRepository.existsById(eventId)).thenReturn(true);

            // Act
            orderService.confirmOrder(event);

            // Assert
            verify(processedEventRepository).existsById(eventId);
            verifyNoMoreInteractions(processedEventRepository);
            verifyNoInteractions(orderRepository);
            verifyNoInteractions(orderMetrics);
        }

        @Test
        @DisplayName("should throw OrderNotFoundException when order does not exist")
        void shouldThrowOrderNotFoundExceptionWhenOrderDoesNotExist() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID nonExistentOrderId = UUID.randomUUID();
            InventoryReservedEvent event = new InventoryReservedEvent(eventId, nonExistentOrderId, Instant.now(), 1);

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.confirmOrder(event))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessage("Order not found with ID: " + nonExistentOrderId);

            verify(orderRepository).findById(nonExistentOrderId);
            verify(processedEventRepository, never()).save(any());
            verifyNoInteractions(orderMetrics);
        }

        @Test
        @DisplayName("should throw IllegalStateException when order status is not PENDING")
        void shouldThrowIllegalStateExceptionWhenOrderStatusIsNotPending() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            InventoryReservedEvent event = new InventoryReservedEvent(eventId, orderId, Instant.now(), 1);

            Order confirmedOrder = Order.builder()
                    .id(orderId)
                    .customerId(UUID.randomUUID())
                    .status(OrderStatus.CONFIRMED)
                    .build();

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(confirmedOrder));

            // Act & Assert
            assertThatThrownBy(() -> orderService.confirmOrder(event))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot transition from CONFIRMED to CONFIRMED");

            verify(processedEventRepository, never()).save(any());
            verifyNoInteractions(orderMetrics);
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrderTests {

        @Test
        @DisplayName("should transition order from PENDING to CANCELLED and record processed event")
        void shouldCancelOrderSuccessfullyWhenStatusIsPending() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            InventoryRejectedEvent event = new InventoryRejectedEvent(
                    eventId, orderId, UUID.randomUUID(), 2, 0, "INSUFFICIENT_STOCK", Instant.now(), 1
            );

            Order pendingOrder = Order.builder()
                    .id(orderId)
                    .customerId(UUID.randomUUID())
                    .status(OrderStatus.PENDING)
                    .build();

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));

            // Act
            orderService.cancelOrder(event);

            // Assert
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

            ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
            verify(processedEventRepository).save(captor.capture());

            ProcessedEvent capturedEvent = captor.getValue();
            assertThat(capturedEvent.getEventId()).isEqualTo(eventId);
            assertThat(capturedEvent.getProcessedAt()).isNotNull();
            verify(afterCommitExecutor).execute(any());
            verify(orderMetrics).orderCancelled();
        }

        @Test
        @DisplayName("should skip processing when event has already been processed (idempotency)")
        void shouldSkipProcessingWhenEventAlreadyProcessed() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            InventoryRejectedEvent event = new InventoryRejectedEvent(
                    eventId, orderId, UUID.randomUUID(), 2, 0, "INSUFFICIENT_STOCK", Instant.now(), 1
            );

            when(processedEventRepository.existsById(eventId)).thenReturn(true);

            // Act
            orderService.cancelOrder(event);

            // Assert
            verify(processedEventRepository).existsById(eventId);
            verifyNoMoreInteractions(processedEventRepository);
            verifyNoInteractions(orderRepository);
            verifyNoInteractions(orderMetrics);
        }

        @Test
        @DisplayName("should throw OrderNotFoundException when order does not exist")
        void shouldThrowOrderNotFoundExceptionWhenOrderDoesNotExist() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID nonExistentOrderId = UUID.randomUUID();
            InventoryRejectedEvent event = new InventoryRejectedEvent(
                    eventId, nonExistentOrderId, UUID.randomUUID(), 2, 0, "INSUFFICIENT_STOCK", Instant.now(), 1
            );

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(event))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessage("Order not found with ID: " + nonExistentOrderId);

            verify(orderRepository).findById(nonExistentOrderId);
            verify(processedEventRepository, never()).save(any());
            verifyNoInteractions(orderMetrics);
        }

        @Test
        @DisplayName("should throw IllegalStateException when order status is not PENDING")
        void shouldThrowIllegalStateExceptionWhenOrderStatusIsNotPending() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            InventoryRejectedEvent event = new InventoryRejectedEvent(
                    eventId, orderId, UUID.randomUUID(), 2, 0, "INSUFFICIENT_STOCK", Instant.now(), 1
            );

            Order cancelledOrder = Order.builder()
                    .id(orderId)
                    .customerId(UUID.randomUUID())
                    .status(OrderStatus.CANCELLED)
                    .build();

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(cancelledOrder));

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(event))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot transition from CANCELLED to CANCELLED");

            verify(processedEventRepository, never()).save(any());
            verifyNoInteractions(orderMetrics);
        }
    }
}
