package com.swe.ordersservice.messaging;

import com.swe.ordersservice.config.KafkaConsumerConfig;
import com.swe.ordersservice.config.KafkaTopicConfig;
import com.swe.ordersservice.event.InventoryReservedEvent;
import com.swe.ordersservice.exception.InvalidEventException;
import com.swe.ordersservice.service.OrderService;
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
}

