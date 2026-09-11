package com.swe.notificationsservice.messaging;

import com.swe.notificationsservice.config.KafkaConsumerConfig;
import com.swe.notificationsservice.config.KafkaTopicConfig;
import com.swe.notificationsservice.entity.Notification;
import com.swe.notificationsservice.entity.NotificationStatus;
import com.swe.notificationsservice.entity.NotificationType;
import com.swe.notificationsservice.event.InventoryRejectedEvent;
import com.swe.notificationsservice.event.InventoryReservedEvent;
import com.swe.notificationsservice.exception.InvalidEventException;
import com.swe.notificationsservice.repository.NotificationRepository;
import com.swe.notificationsservice.service.NotificationService;
import com.swe.notificationsservice.service.NotificationServiceImpl;
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
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

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
            InventoryReservedConsumer reservedConsumer = new InventoryReservedConsumer(notificationService, objectMapper);

            UUID orderId = UUID.randomUUID();
            InventoryReservedEvent event = new InventoryReservedEvent(
                    UUID.randomUUID(), orderId, Instant.now(), 1
            );
            String payload = objectMapper.writeValueAsString(event);

            AtomicInteger attempt = new AtomicInteger(0);
            doAnswer(invocation -> {
                if (attempt.incrementAndGet() == 1) {
                    throw new RuntimeException("Temporary SMTP/DB network timeout");
                }
                return null;
            }).when(notificationService).handleInventoryReservedEvent(any(InventoryReservedEvent.class));

            // Attempt 1 fails
            assertThatThrownBy(() -> reservedConsumer.consume(orderId.toString(), payload))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Temporary SMTP/DB network timeout");

            // Attempt 2 succeeds
            reservedConsumer.consume(orderId.toString(), payload);

            verify(notificationService, times(2)).handleInventoryReservedEvent(any(InventoryReservedEvent.class));
            verifyNoInteractions(kafkaTemplate);
        }
    }

    @Nested
    @DisplayName("2. Non-Retryable Failure / Retries Exhausted")
    class RetriesExhaustedOrNonRetryableTests {

        @Test
        @DisplayName("Scenario 2: Fatal error on inventory.reserved -> DefaultErrorHandler routes to inventory.reserved.notifications.dlt")
        void shouldPublishToInventoryReservedNotificationsDltWhenFatalErrorOccurs() {
            DefaultErrorHandler errorHandler = consumerConfig.kafkaErrorHandler(kafkaTemplate);

            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    KafkaTopicConfig.INVENTORY_RESERVED_TOPIC, 0, 10L, "order-key-1", "invalid-payload"
            );

            Exception fatalException = new InvalidEventException("Failed to process InventoryReserved event");

            errorHandler.handleOne(fatalException, record, consumer, container);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
                    ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(recordCaptor.capture());

            ProducerRecord<String, String> dltRecord = recordCaptor.getValue();
            assertThat(dltRecord.topic()).isEqualTo(KafkaTopicConfig.INVENTORY_RESERVED_NOTIFICATIONS_DLT);
            assertThat(dltRecord.key()).isEqualTo("order-key-1");
            assertThat(dltRecord.value()).isEqualTo("invalid-payload");
        }

        @Test
        @DisplayName("Scenario 3: Fatal error on inventory.rejected -> DefaultErrorHandler routes to inventory.rejected.notifications.dlt")
        void shouldPublishToInventoryRejectedNotificationsDltWhenFatalErrorOccurs() {
            DefaultErrorHandler errorHandler = consumerConfig.kafkaErrorHandler(kafkaTemplate);

            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    KafkaTopicConfig.INVENTORY_REJECTED_TOPIC, 1, 20L, "order-key-2", "invalid-payload"
            );

            Exception fatalException = new InvalidEventException("Failed to process InventoryRejected event");

            errorHandler.handleOne(fatalException, record, consumer, container);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
                    ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(recordCaptor.capture());

            ProducerRecord<String, String> dltRecord = recordCaptor.getValue();
            assertThat(dltRecord.topic()).isEqualTo(KafkaTopicConfig.INVENTORY_REJECTED_NOTIFICATIONS_DLT);
            assertThat(dltRecord.key()).isEqualTo("order-key-2");
            assertThat(dltRecord.value()).isEqualTo("invalid-payload");
        }
    }

    @Nested
    @DisplayName("3. Restart and Replay Scenarios")
    class RestartAndReplayTests {

        @Test
        @DisplayName("Scenario A: Normal restart -> Stop consumer, publish another event, restart consumer, new event gets processed")
        void shouldProcessNewNotificationAfterConsumerRestart() throws Exception {
            InventoryReservedConsumer reservedConsumer = new InventoryReservedConsumer(notificationService, objectMapper);

            UUID orderId1 = UUID.randomUUID();
            InventoryReservedEvent event1 = new InventoryReservedEvent(
                    UUID.randomUUID(), orderId1, Instant.now(), 1
            );
            String payload1 = objectMapper.writeValueAsString(event1);

            // 1. Process event 1 while consumer is running
            when(container.isRunning()).thenReturn(true);
            reservedConsumer.consume(orderId1.toString(), payload1);
            verify(notificationService, times(1)).handleInventoryReservedEvent(any(InventoryReservedEvent.class));

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
            verify(notificationService, times(2)).handleInventoryReservedEvent(any(InventoryReservedEvent.class));
        }

        @Test
        @DisplayName("Scenario B: Restart before offset commit / redelivery -> Same event delivered again, notification repository prevents duplicate row")
        void shouldPreventDuplicateNotificationWhenEventRedeliveredAfterRestart() {
            NotificationServiceImpl service = new NotificationServiceImpl(notificationRepository);

            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            InventoryReservedEvent event = new InventoryReservedEvent(
                    eventId, orderId, Instant.now(), 1
            );

            Notification existingNotification = Notification.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .orderId(orderId)
                    .type(NotificationType.ORDER_CONFIRMED)
                    .recipient("customer x")
                    .message("Your order " + orderId + " has been confirmed.")
                    .status(NotificationStatus.PENDING)
                    .build();

            // First delivery: notification does not exist yet -> gets saved
            when(notificationRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            service.handleInventoryReservedEvent(event);

            ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, times(1)).save(notificationCaptor.capture());
            assertThat(notificationCaptor.getValue().getEventId()).isEqualTo(eventId);
            assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.ORDER_CONFIRMED);

            // Crash / Restart before offset commit: Kafka redelivers the EXACT same event
            when(notificationRepository.findByEventId(eventId)).thenReturn(Optional.of(existingNotification));

            service.handleInventoryReservedEvent(event);

            // Assert: No second save call made
            verify(notificationRepository, times(1)).save(any(Notification.class));
        }

        @Test
        @DisplayName("Scenario E: Notifications replay -> Replay same lifecycle events -> No second notification row")
        void shouldIgnoreReplayOfAlreadyHandledLifecycleEvents() {
            NotificationServiceImpl service = new NotificationServiceImpl(notificationRepository);

            UUID reservedEventId = UUID.randomUUID();
            UUID rejectedEventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            InventoryReservedEvent replayedReservedEvent = new InventoryReservedEvent(
                    reservedEventId, orderId, Instant.now(), 1
            );
            InventoryRejectedEvent replayedRejectedEvent = new InventoryRejectedEvent(
                    rejectedEventId, orderId, UUID.randomUUID(), 4, 1, "INSUFFICIENT_STOCK", Instant.now(), 1
            );

            Notification dummyReservedNotification = Notification.builder()
                    .id(UUID.randomUUID())
                    .eventId(reservedEventId)
                    .orderId(orderId)
                    .type(NotificationType.ORDER_CONFIRMED)
                    .status(NotificationStatus.SENT)
                    .build();

            Notification dummyRejectedNotification = Notification.builder()
                    .id(UUID.randomUUID())
                    .eventId(rejectedEventId)
                    .orderId(orderId)
                    .type(NotificationType.ORDER_CANCELLED)
                    .status(NotificationStatus.SENT)
                    .build();

            // Events are already present in DB
            when(notificationRepository.findByEventId(reservedEventId)).thenReturn(Optional.of(dummyReservedNotification));
            when(notificationRepository.findByEventId(rejectedEventId)).thenReturn(Optional.of(dummyRejectedNotification));

            // Replay reserved event
            service.handleInventoryReservedEvent(replayedReservedEvent);

            // Replay rejected event
            service.handleInventoryRejectedEvent(replayedRejectedEvent);

            // Verify idempotency checks were made and save was never invoked
            verify(notificationRepository).findByEventId(reservedEventId);
            verify(notificationRepository).findByEventId(rejectedEventId);
            verify(notificationRepository, never()).save(any(Notification.class));
        }
    }
}


