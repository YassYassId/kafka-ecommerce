package com.swe.notificationsservice.messaging;

import com.swe.notificationsservice.event.InventoryReservedEvent;
import com.swe.notificationsservice.service.NotificationService;
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

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory.reserved", groupId = "notifications-service")
    public void consume(String key, String payload){
        try {
            InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);

            log.info("Received InventoryReserved event. eventId={}, orderId={}, key={}", event.eventId(), event.orderId(), key);

            notificationService.handleInventoryReservedEvent(event);
        } catch (JacksonException e) {
            log.error("Error processing InventoryReserved event. key={}, payload={}, error={}", key, payload, e.getMessage(), e);
            throw new IllegalStateException("Failed to process InventoryReserved event", e);

        }
    }
}
