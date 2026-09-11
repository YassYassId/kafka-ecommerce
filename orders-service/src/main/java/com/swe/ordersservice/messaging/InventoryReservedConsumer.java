package com.swe.ordersservice.messaging;

import com.swe.ordersservice.event.InventoryReservedEvent;
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
public class InventoryReservedConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory.reserved", groupId = "orders-service")
    public void consume(String key, String payload) {
        try {
            InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);

            log.info("Received InventoryReserved event. eventId={}, orderId={}, key={}", event.eventId(), event.orderId(), key);

            orderService.confirmOrder(event);
        } catch (JacksonException e) {
            log.error("Failed to deserialize InventoryReserved event. payload={}", payload, e);
            throw new InvalidEventException("Failed to deserialize InventoryReserved event", e);
        }
    }
}

