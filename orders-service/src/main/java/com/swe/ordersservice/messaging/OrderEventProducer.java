package com.swe.ordersservice.messaging;

import com.swe.ordersservice.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public CompletableFuture<SendResult<String, String>> publishOrderCreatedEvent(String orderId, String payload, String correlationId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaTopicConfig.ORDER_CREATED_TOPIC,
                orderId,
                payload
        );

        record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));

        return kafkaTemplate.send(record);
    }
}
