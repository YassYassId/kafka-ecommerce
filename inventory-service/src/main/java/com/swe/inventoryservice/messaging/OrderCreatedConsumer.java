package com.swe.inventoryservice.messaging;

import com.swe.inventoryservice.event.OrderCreatedEvent;
import com.swe.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.created", groupId = "inventory-service")
    public void consume(String key, String payload) {

        try {
            OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
            log.info(
                    "Received OrderCreated event. eventId={}, orderId={}, key={}",
                    event.eventId(),
                    event.orderId(),
                    key
            );
            inventoryService.processOrder(event);
        } catch (JacksonException e) {
            log.error(
                    "Failed to deserialize OrderCreated event. payload={}",
                    payload,
                    e
            );
            throw new IllegalStateException("Failed to deserialize OrderCreated event", e);
        }
    }
}
