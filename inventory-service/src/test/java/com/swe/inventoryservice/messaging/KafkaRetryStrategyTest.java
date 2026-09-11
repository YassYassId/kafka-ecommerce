package com.swe.inventoryservice.messaging;

import com.swe.inventoryservice.config.KafkaConsumerConfig;
import com.swe.inventoryservice.config.KafkaTopicConfig;
import com.swe.inventoryservice.entity.InventoryItem;
import com.swe.inventoryservice.entity.ProcessedEvent;
import com.swe.inventoryservice.event.OrderCreatedEvent;
import com.swe.inventoryservice.event.OrderCreatedItem;
import com.swe.inventoryservice.exception.InvalidEventException;
import com.swe.inventoryservice.outbox.OutboxEvent;
import com.swe.inventoryservice.outbox.OutboxEventRepository;
import com.swe.inventoryservice.repository.InventoryItemRepository;
import com.swe.inventoryservice.repository.ProcessedEventRepository;
import com.swe.inventoryservice.service.InventoryService;
import com.swe.inventoryservice.service.InventoryTransactionServiceImpl;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaRetryStrategyTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

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
        @DisplayName("Scenario 1: Listener fails temporarily, retries and later succeeds -> not sent to DLT")
        void shouldRetryAndSucceedWithoutSendingToDlt() throws Exception {
            OrderCreatedConsumer orderCreatedConsumer = new OrderCreatedConsumer(inventoryService, objectMapper);

            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId, orderId, UUID.randomUUID(),
                    List.of(new OrderCreatedItem(UUID.randomUUID(), 2)),
                    Instant.now(), 1
            );
            String payload = objectMapper.writeValueAsString(event);

            AtomicInteger attempt = new AtomicInteger(0);
            doAnswer(invocation -> {
                if (attempt.incrementAndGet() == 1) {
                    throw new RuntimeException("Temporary DB connection timeout");
                }
                return null;
            }).when(inventoryService).processOrder(any(OrderCreatedEvent.class));

            // Attempt 1 fails
            assertThatThrownBy(() -> orderCreatedConsumer.consume(orderId.toString(), payload))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Temporary DB connection timeout");

            // Attempt 2 succeeds
            orderCreatedConsumer.consume(orderId.toString(), payload);

            verify(inventoryService, times(2)).processOrder(any(OrderCreatedEvent.class));
            // Kafka template (DLT producer) is never called
            verifyNoInteractions(kafkaTemplate);
        }
    }

    @Nested
    @DisplayName("2. Failure On Every Attempt")
    class FailureOnEveryAttemptTests {

        @Test
        @DisplayName("Scenario 2: Retries exhausted -> DefaultErrorHandler invokes recoverer to publish to DLT")
        void shouldPublishToDltWhenRetriesAreExhausted() {
            DefaultErrorHandler errorHandler = consumerConfig.kafkaErrorHandler(kafkaTemplate);

            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "order.created", 0, 10L, "order-key", "invalid-payload"
            );

            Exception fatalException = new InvalidEventException("Fatal deserialization error", new RuntimeException());

            // Non-retryable exception is handled by DefaultErrorHandler
            errorHandler.handleOne(fatalException, record, consumer, container);

            ArgumentCaptor<org.apache.kafka.clients.producer.ProducerRecord<String, String>> recordCaptor =
                    ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);
            verify(kafkaTemplate).send(recordCaptor.capture());

            org.apache.kafka.clients.producer.ProducerRecord<String, String> dltRecord = recordCaptor.getValue();
            assertThat(dltRecord.topic()).isEqualTo(KafkaTopicConfig.ORDER_CREATED_DLT);
            assertThat(dltRecord.key()).isEqualTo("order-key");
            assertThat(dltRecord.value()).isEqualTo("invalid-payload");
        }
    }

    @Nested
    @DisplayName("3. Business Stock Rejection")
    class BusinessStockRejectionTests {

        @Test
        @DisplayName("Scenario 3: Stock insufficient -> InventoryRejected produced to outbox, no exception, no Kafka retry")
        void shouldProduceInventoryRejectedWithoutTriggeringKafkaRetry() {
            InventoryTransactionServiceImpl transactionService = new InventoryTransactionServiceImpl(
                    inventoryItemRepository, processedEventRepository, outboxEventRepository, objectMapper
            );

            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId, orderId, UUID.randomUUID(),
                    List.of(new OrderCreatedItem(productId, 10)),
                    Instant.now(), 1
            );

            InventoryItem itemInStock = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .productId(productId)
                    .availableQuantity(2) // only 2 available, requested 10
                    .reservedQuantity(0)
                    .version(0L)
                    .build();

            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(inventoryItemRepository.findByProductId(productId)).thenReturn(Optional.of(itemInStock));

            // Business transaction executes smoothly without throwing unhandled exceptions
            transactionService.process(event);

            // Verify stock was not deducted
            assertThat(itemInStock.getAvailableQuantity()).isEqualTo(2);

            // Verify InventoryRejected outbox event created
            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(outboxCaptor.capture());
            assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("InventoryRejected");
            assertThat(outboxCaptor.getValue().getPayload()).contains("INSUFFICIENT_STOCK");

            // Verify event marked as processed
            verify(processedEventRepository).save(any(ProcessedEvent.class));

            // Kafka template (DLT) is NOT invoked
            verifyNoInteractions(kafkaTemplate);
        }
    }

    @Nested
    @DisplayName("4. Restart and Replay Scenarios")
    class RestartAndReplayTests {

        @Test
        @DisplayName("Scenario A: Normal restart -> Stop consumer, publish another event, restart consumer, new event gets processed")
        void shouldProcessNewEventAfterConsumerRestart() throws Exception {
            OrderCreatedConsumer orderCreatedConsumer = new OrderCreatedConsumer(inventoryService, objectMapper);

            UUID orderId1 = UUID.randomUUID();
            OrderCreatedEvent event1 = new OrderCreatedEvent(
                    UUID.randomUUID(), orderId1, UUID.randomUUID(),
                    List.of(new OrderCreatedItem(UUID.randomUUID(), 2)),
                    Instant.now(), 1
            );
            String payload1 = objectMapper.writeValueAsString(event1);

            // 1. Process event 1 while consumer is running
            when(container.isRunning()).thenReturn(true);
            orderCreatedConsumer.consume(orderId1.toString(), payload1);
            verify(inventoryService, times(1)).processOrder(any(OrderCreatedEvent.class));

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
            OrderCreatedEvent event2 = new OrderCreatedEvent(
                    UUID.randomUUID(), orderId2, UUID.randomUUID(),
                    List.of(new OrderCreatedItem(UUID.randomUUID(), 5)),
                    Instant.now(), 1
            );
            String payload2 = objectMapper.writeValueAsString(event2);

            orderCreatedConsumer.consume(orderId2.toString(), payload2);
            verify(inventoryService, times(2)).processOrder(any(OrderCreatedEvent.class));
        }

        @Test
        @DisplayName("Scenario B: Restart before offset commit / redelivery -> Same event delivered again, processed_events prevents duplicate side effect")
        void shouldPreventDuplicateSideEffectWhenRestartBeforeOffsetCommitOccurs() {
            InventoryTransactionServiceImpl transactionService = new InventoryTransactionServiceImpl(
                    inventoryItemRepository, processedEventRepository, outboxEventRepository, objectMapper
            );

            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId, orderId, UUID.randomUUID(),
                    List.of(new OrderCreatedItem(productId, 2)),
                    Instant.now(), 1
            );

            InventoryItem item = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .productId(productId)
                    .availableQuantity(10)
                    .reservedQuantity(0)
                    .version(0L)
                    .build();

            // First delivery: Event is processed successfully
            when(processedEventRepository.existsById(eventId)).thenReturn(false);
            when(inventoryItemRepository.findByProductId(productId)).thenReturn(Optional.of(item));

            transactionService.process(event);

            assertThat(item.getAvailableQuantity()).isEqualTo(8);
            assertThat(item.getReservedQuantity()).isEqualTo(2);
            verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
            verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));

            // Crash / Restart before offset commit happens: Kafka redelivers the EXACT same event
            // Now processedEventRepository has the event recorded
            when(processedEventRepository.existsById(eventId)).thenReturn(true);

            transactionService.process(event);

            // Assert: Stock quantities remain exactly 8 and 2 (no double deduction)
            assertThat(item.getAvailableQuantity()).isEqualTo(8);
            assertThat(item.getReservedQuantity()).isEqualTo(2);
            // Assert: No additional outbox events or processed events saved
            verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
            verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
        }

        @Test
        @DisplayName("Scenario C: Manual replay -> Republish an already processed event with same eventId -> No duplicate stock reservation, no extra outbox event")
        void shouldPreventDuplicateReservationOnManualEventReplay() {
            InventoryTransactionServiceImpl transactionService = new InventoryTransactionServiceImpl(
                    inventoryItemRepository, processedEventRepository, outboxEventRepository, objectMapper
            );

            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            OrderCreatedEvent replayedEvent = new OrderCreatedEvent(
                    eventId, orderId, UUID.randomUUID(),
                    List.of(new OrderCreatedItem(productId, 3)),
                    Instant.now(), 1
            );

            // Simulating event already recorded in processed_events table from past run
            when(processedEventRepository.existsById(eventId)).thenReturn(true);

            transactionService.process(replayedEvent);

            // Verify idempotency check intercepted the replayed event
            verify(processedEventRepository).existsById(eventId);
            verifyNoInteractions(inventoryItemRepository);
            verifyNoInteractions(outboxEventRepository);
            verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        }
    }
}

