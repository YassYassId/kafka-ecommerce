package com.swe.ordersservice.messaging;

import com.swe.ordersservice.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public CompletableFuture<SendResult<String, String>> publishOrderCreatedEvent(String orderId, String payload) {
        return kafkaTemplate.send(
                KafkaTopicConfig.ORDER_CREATED_TOPIC,
                orderId,
                payload
        );
    }
}
