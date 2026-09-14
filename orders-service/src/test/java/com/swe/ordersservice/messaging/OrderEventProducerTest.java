package com.swe.ordersservice.messaging;

import com.swe.ordersservice.config.KafkaTopicConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OrderEventProducer orderEventProducer;

    @Test
    @DisplayName("should publish order created event with correlationId header to expected Kafka topic")
    void shouldPublishOrderCreatedEventSuccessfully() {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        String payload = "{\"orderId\":\"" + orderId + "\",\"status\":\"PENDING\"}";
        String correlationId = UUID.randomUUID().toString();

        CompletableFuture<SendResult<String, String>> expectedFuture = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(expectedFuture);

        // Act
        CompletableFuture<SendResult<String, String>> actualFuture =
                orderEventProducer.publishOrderCreatedEvent(orderId, payload, correlationId);

        // Assert
        assertThat(actualFuture).isSameAs(expectedFuture);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, String> capturedRecord = recordCaptor.getValue();
        assertThat(capturedRecord.topic()).isEqualTo(KafkaTopicConfig.ORDER_CREATED_TOPIC);
        assertThat(capturedRecord.key()).isEqualTo(orderId);
        assertThat(capturedRecord.value()).isEqualTo(payload);

        Header correlationHeader = capturedRecord.headers().lastHeader("X-Correlation-Id");
        assertThat(correlationHeader).isNotNull();
        assertThat(new String(correlationHeader.value(), StandardCharsets.UTF_8)).isEqualTo(correlationId);
    }
}
