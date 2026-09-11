package com.swe.ordersservice.config;

import com.swe.ordersservice.exception.InvalidEventException;
import com.swe.ordersservice.exception.OrderNotFoundException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();

    @Nested
    @DisplayName("kafkaErrorHandler")
    class KafkaErrorHandlerTests {

        @Test
        @DisplayName("should configure DefaultErrorHandler with DeadLetterPublishingRecoverer and FixedBackOff")
        void shouldConfigureDefaultErrorHandler() {
            DefaultErrorHandler errorHandler = consumerConfig.kafkaErrorHandler(kafkaTemplate);

            assertThat(errorHandler).isNotNull();

            Object failureTracker = ReflectionTestUtils.getField(errorHandler, "failureTracker");
            assertThat(failureTracker).isNotNull();

            // Verify BackOff configuration (2 retries, 2000ms interval)
            BackOff backOff = (BackOff) ReflectionTestUtils.getField(failureTracker, "backOff");
            assertThat(backOff).isInstanceOf(FixedBackOff.class);
            FixedBackOff fixedBackOff = (FixedBackOff) backOff;
            assertThat(fixedBackOff.getInterval()).isEqualTo(2000L);
            assertThat(fixedBackOff.getMaxAttempts()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should classify non-retryable exceptions (InvalidEventException, OrderNotFoundException)")
        @SuppressWarnings("unchecked")
        void shouldClassifyNonRetryableExceptions() {
            DefaultErrorHandler errorHandler = consumerConfig.kafkaErrorHandler(kafkaTemplate);

            Object exceptionMatcher = ReflectionTestUtils.getField(errorHandler, "exceptionMatcher");
            assertThat(exceptionMatcher).isNotNull();

            java.util.Map<Class<? extends Throwable>, Boolean> entries =
                    (java.util.Map<Class<? extends Throwable>, Boolean>)
                            ReflectionTestUtils.invokeMethod(exceptionMatcher, "getEntries");

            assertThat(entries).isNotNull();
            assertThat(entries.get(InvalidEventException.class)).isFalse();
            assertThat(entries.get(OrderNotFoundException.class)).isFalse();
        }


        @Test
        @DisplayName("should route failed inventory.reserved messages to inventory.reserved.dlt preserving partition number")
        @SuppressWarnings("unchecked")
        void shouldRouteInventoryReservedToDltPreservingPartition() {
            DefaultErrorHandler errorHandler = consumerConfig.kafkaErrorHandler(kafkaTemplate);

            Object failureTracker = ReflectionTestUtils.getField(errorHandler, "failureTracker");
            assertThat(failureTracker).isNotNull();

            DeadLetterPublishingRecoverer recoverer = (DeadLetterPublishingRecoverer) ReflectionTestUtils.getField(failureTracker, "recoverer");
            assertThat(recoverer).isNotNull();

            BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver =
                    (BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition>)
                            ReflectionTestUtils.getField(recoverer, "destinationResolver");
            assertThat(destinationResolver).isNotNull();

            ConsumerRecord<String, String> record = new ConsumerRecord<>("inventory.reserved", 1, 100L, "key-1", "payload-1");
            TopicPartition targetPartition = destinationResolver.apply(record, new RuntimeException("processing failed"));

            assertThat(targetPartition.topic()).isEqualTo(KafkaTopicConfig.INVENTORY_RESERVED_DLT);
            assertThat(targetPartition.partition()).isEqualTo(1);
        }

        @Test
        @DisplayName("should route failed inventory.rejected messages to inventory.rejected.dlt preserving partition number")
        @SuppressWarnings("unchecked")
        void shouldRouteInventoryRejectedToDltPreservingPartition() {
            DefaultErrorHandler errorHandler = consumerConfig.kafkaErrorHandler(kafkaTemplate);

            Object failureTracker = ReflectionTestUtils.getField(errorHandler, "failureTracker");
            assertThat(failureTracker).isNotNull();

            DeadLetterPublishingRecoverer recoverer = (DeadLetterPublishingRecoverer) ReflectionTestUtils.getField(failureTracker, "recoverer");
            assertThat(recoverer).isNotNull();

            BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver =
                    (BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition>)
                            ReflectionTestUtils.getField(recoverer, "destinationResolver");
            assertThat(destinationResolver).isNotNull();

            ConsumerRecord<String, String> record = new ConsumerRecord<>("inventory.rejected", 2, 200L, "key-2", "payload-2");
            TopicPartition targetPartition = destinationResolver.apply(record, new RuntimeException("processing failed"));

            assertThat(targetPartition.topic()).isEqualTo(KafkaTopicConfig.INVENTORY_REJECTED_DLT);
            assertThat(targetPartition.partition()).isEqualTo(2);
        }
    }
}
