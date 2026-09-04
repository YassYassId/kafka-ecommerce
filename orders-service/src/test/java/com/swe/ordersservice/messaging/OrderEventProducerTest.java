package com.swe.ordersservice.messaging;

import com.swe.ordersservice.config.KafkaTopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OrderEventProducer orderEventProducer;

    @Test
    @DisplayName("should publish order created event to expected Kafka topic with orderId key and payload")
    void shouldPublishOrderCreatedEventSuccessfully() {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        String payload = "{\"orderId\":\"" + orderId + "\",\"status\":\"PENDING\"}";

        CompletableFuture<SendResult<String, String>> expectedFuture = new CompletableFuture<>();
        when(kafkaTemplate.send(KafkaTopicConfig.ORDER_CREATED_TOPIC, orderId, payload))
                .thenReturn(expectedFuture);

        // Act
        CompletableFuture<SendResult<String, String>> actualFuture =
                orderEventProducer.publishOrderCreatedEvent(orderId, payload);

        // Assert
        assertThat(actualFuture).isSameAs(expectedFuture);
        verify(kafkaTemplate).send(
                eq(KafkaTopicConfig.ORDER_CREATED_TOPIC),
                eq(orderId),
                eq(payload)
        );
    }
}
