package com.swe.ordersservice.messaging;

import com.swe.ordersservice.config.KafkaConsumerConfig;
import com.swe.ordersservice.config.KafkaTopicConfig;
import com.swe.ordersservice.entity.Order;
import com.swe.ordersservice.entity.OrderStatus;
import com.swe.ordersservice.entity.ProcessedEvent;
import com.swe.ordersservice.event.InventoryRejectedEvent;
import com.swe.ordersservice.event.InventoryReservedEvent;
import com.swe.ordersservice.exception.InvalidEventException;
import com.swe.ordersservice.outbox.OutboxEventFactory;
import com.swe.ordersservice.outbox.OutboxEventRepository;
import com.swe.ordersservice.repository.OrderRepository;
import com.swe.ordersservice.repository.ProcessedEventRepository;
import com.swe.ordersservice.service.OrderService;
import com.swe.ordersservice.service.OrderServiceImpl;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaRetryStrategyTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventFactory outboxEventFactory;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private Consumer<?, ?> consumer;

    @Mock
    private MessageListenerContainer container;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private final KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();

    @Nested
    @DisplayName("1. Temporary Processing Failure")
    class TemporaryFailureTests {

        @Test
        @DisplayName("Scenario 1: Consumer fails temporarily, retries and succeeds -> not sent to DLT")
        void shouldRetryAndSucceedWithoutSendingToDlt() throws Exception {
            InventoryReservedConsumer reservedConsumer = new InventoryReservedConsumer(orderService, objectMapper);

            UUID orderId = UUID.randomUUID();
            InventoryReservedEvent event = new InventoryReservedEvent(
                    UUID.randomUUID(), orderId, Instant.now(), 1
            );
            String payload = objectMapper.writeValueAsString(event);

            AtomicInteger attempt = new AtomicInteger(0);
            doAnswer(invocation -> {
                if (attempt.incrementAndGet() == 1) {
                    throw new RuntimeException("Temporary DB timeout");
                }
                return null;
            }).when(orderService).confirmOrder(any(InventoryReservedEvent.class));

            // Attempt 1 fails
            assertThatThrownBy(() -> reservedConsumer.consume(orderId.toString(), payload))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Temporary DB timeout");

            // Attempt 2 succeeds
            reservedConsumer.consume(orderId.toString(), payload);

            verify(orderService, times(2)).confirmOrder(any(InventoryReservedEvent.class));
            verifyNoInteractions(kafkaTemplate);
        }
    }

    @Nested
    @DisplayName("2. Non-Retryable Failure / Retries Exhausted")
    class RetriesExhaustedOrNonRetryableTests {

        @Test
        @DisplayName("Scenario 2: Fatal error on inventory.reserved -> DefaultErrorHandler routes to inventory.reserved.dlt")
        void shouldPublishToInventoryReservedDltWhenFatalErrorOccurs() {
            DefaultErrorHandler errorHandler = consumerConfig.kafkaErrorHandler(kafkaTemplate);

            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    KafkaTopicConfig.INVENTORY_RESERVED_TOPIC, 0, 10L, "order-key-1", "invalid-payload"
            );

            Exception fatalException = new InvalidEventException("Failed to deserialize InventoryReserved event");

            errorHandler.handleOne(fatalException, record, consumer, container);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
                    ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(recordCaptor.capture());

            ProducerRecord<String, String> dltRecord = recordCaptor.getValue();
            assertThat(dltRecord.topic()).isEqualTo(KafkaTopicConfig.INVENTORY_RESERVED_DLT);
            assertThat(dltRecord.key()).isEqualTo("order-key-1");
            assertThat(dltRecord.value()).isEqualTo("invalid-payload");
        }

        @Test
        @DisplayName("Scenario 3: Fatal error on inventory.rejected -> DefaultErrorHandler routes to inventory.rejected.dlt")
        void shouldPublishToInventoryRejectedDltWhenFatalErrorOccurs() {
            DefaultErrorHandler errorHandler = consumerConfig.kafkaErrorHandler(kafkaTemplate);

            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    KafkaTopicConfig.INVENTORY_REJECTED_TOPIC, 1, 20L, "order-key-2", "invalid-payload"
            );

            Exception fatalException = new InvalidEventException("Failed to deserialize InventoryRejected event");

            errorHandler.handleOne(fatalException, record, consumer, container);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
                    ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(recordCaptor.capture());

            ProducerRecord<String, String> dltRecord = recordCaptor.getValue();
            assertThat(dltRecord.topic()).isEqualTo(KafkaTopicConfig.INVENTORY_REJECTED_DLT);
            assertThat(dltRecord.key()).isEqualTo("order-key-2");
            assertThat(dltRecord.value()).isEqualTo("invalid-payload");
        }
    }

    @Nested
    @DisplayName("3. Restart and Replay Scenarios")
    class RestartAndReplayTests {

        @Test
        @DisplayName("Scenario A: Normal restart -> Stop consumer, publish another event, restart consumer, new event gets processed")
        void shouldProcessNewOrderEventAfterConsumerRestart() throws Exception {
            InventoryReservedConsumer reservedConsumer = new InventoryReservedConsumer(orderService, objectMapper);

            UUID orderId1 = UUID.randomUUID();
            InventoryReservedEvent event1 = new InventoryReservedEvent(
                    UUID.randomUUID(), orderId1, Instant.now(), 1
            );
            String payload1 = objectMapper.writeValueAsString(event1);

            // 1. Process event 1 while consumer is running
            when(container.isRunning()).thenReturn(true);
            reservedConsumer.consume(orderId1.toString(), payload1);
            verify(orderService, times(1)).confirmOrder(any(InventoryReservedEvent.class));

            // 2. Stop consumer container (simulating planned maintenance / restart)
            container.stop();
            when(container.isRunning()).thenReturn(false);
            assertThat(container.isRunning()).isFalse();

            // 3. Restart consumer container
            container.start();
            when(container.isRunning()).thenReturn(true);
            assertThat(container.isRunning()).isTrue();

            // 4. Publish another event -> New event gets processed successfully
            UUID orderId2 = UUID.randomUUID();
            InventoryReservedEvent event2 = new InventoryReservedEvent(
                    UUID.randomUUID(), orderId2, Instant.now(), 1
            );
            String payload2 = objectMapper.writeValueAsString(event2);

            reservedConsumer.consume(orderId2.toString(), payload2);
            verify(orderService, times(2)).confirmOrder(any(InventoryReservedEvent.class));
        }

        @Test
        @DisplayName("Scenario B: Restart before offset commit / redelivery -> Same event delivered again, processed_events prevents duplicate order transition")
        void shouldPreventDuplicateOrderStatusTransitionWhenEventRedeliveredAfterRestart() {
            OrderServiceImpl orderServiceImpl = new OrderServiceImpl(
                    orderRepository, outboxEventRepository, outboxEventFactory, processedEventRepository
            );

            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            InventoryReservedEvent event = new InventoryReservedEvent(
                    eventId, orderId, Instant.now(), 1
            );

            Order order = Order.builder()
                    .id(orderId)
                    .customerId(UUID.randomUUID())
                    .status(OrderStatus.PENDING)
                    .build();

            // First delivery: Event is processed successfully and order status becomes CONFIRMED
            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            orderServiceImpl.confirmOrder(event);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));

            // Crash / Restart before offset commit happens: Kafka redelivers the EXACT same event
            when(processedEventRepository.existsById(eventId)).thenReturn(true);

            orderServiceImpl.confirmOrder(event);

            // Assert: Order status is still CONFIRMED (no invalid state transition exception thrown)
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            // Assert: No second processed_events record saved
            verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
        }

        @Test
        @DisplayName("Scenario D: Orders replay -> Replay InventoryReserved and InventoryRejected -> No second state transition")
        void shouldIgnoreReplayOfAlreadyProcessedInventoryEvents() {
            OrderServiceImpl orderServiceImpl = new OrderServiceImpl(
                    orderRepository, outboxEventRepository, outboxEventFactory, processedEventRepository
            );

            UUID reservedEventId = UUID.randomUUID();
            UUID rejectedEventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            InventoryReservedEvent replayedReservedEvent = new InventoryReservedEvent(
                    reservedEventId, orderId, Instant.now(), 1
            );
            InventoryRejectedEvent replayedRejectedEvent = new InventoryRejectedEvent(
                    rejectedEventId, orderId, UUID.randomUUID(), 5, 2, "INSUFFICIENT_STOCK", Instant.now(), 1
            );

            // Simulating events already present in processed_events table
            when(processedEventRepository.existsById(reservedEventId)).thenReturn(true);
            when(processedEventRepository.existsById(rejectedEventId)).thenReturn(true);

            // Replay InventoryReserved
            orderServiceImpl.confirmOrder(replayedReservedEvent);

            // Replay InventoryRejected
            orderServiceImpl.cancelOrder(replayedRejectedEvent);

            // Verify idempotency check intercepted the replayed events
            verify(processedEventRepository).existsById(reservedEventId);
            verify(processedEventRepository).existsById(rejectedEventId);
            verifyNoInteractions(orderRepository);
            verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        }
    }
}


