package com.swe.notificationsservice.messaging;

import com.swe.notificationsservice.event.InventoryRejectedEvent;
import com.swe.notificationsservice.exception.InvalidEventException;
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
public class InventoryRejectedConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory.rejected", groupId = "notifications-service")
    public void consume(String key, String payload){
        try {
            InventoryRejectedEvent event = objectMapper.readValue(payload, InventoryRejectedEvent.class);

            log.info("Received InventoryRejected event. eventId={}, orderId={}, key={}", event.eventId(), event.orderId(), key);

            notificationService.handleInventoryRejectedEvent(event);
        } catch (JacksonException e) {
            log.error("Error processing InventoryRejected event. key={}, payload={}, error={}", key, payload, e.getMessage(), e);
            throw new InvalidEventException("Failed to process InventoryRejected event", e);
        }
    }
}

