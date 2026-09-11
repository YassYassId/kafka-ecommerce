package com.swe.ordersservice.messaging;

import com.swe.ordersservice.event.InventoryRejectedEvent;
import com.swe.ordersservice.exception.InvalidEventException;
import com.swe.ordersservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryRejectedConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory.rejected", groupId = "orders-service")
    public void consume(String key, String payload) {
        try {
            InventoryRejectedEvent event = objectMapper.readValue(payload, InventoryRejectedEvent.class);

            log.info("Received InventoryRejected event. eventId={}, orderId={}, key={}", event.eventId(), event.orderId(), key);

            orderService.cancelOrder(event);
        } catch (JacksonException e) {
            log.error("Error occurred while processing InventoryRejected event", e);
            throw new InvalidEventException("Failed to deserialize InventoryRejected event", e);
        }
    }
}

