package com.swe.notificationsservice.messaging;

import com.swe.notificationsservice.event.InventoryRejectedEvent;
import com.swe.notificationsservice.exception.InvalidEventException;
import com.swe.notificationsservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryRejectedConsumer {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory.rejected", groupId = "notifications-service")
    public void consume(@Header(KafkaHeaders.RECEIVED_KEY) String key,
                        @Payload String payload,
                        @Header(name = CORRELATION_HEADER, required = false) String correlationId){

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();

            log.warn("Received InventoryRejected event without correlation ID. Generated fallback correlationId");
        }

        try {
            MDC.put(MDC_KEY, correlationId);

            InventoryRejectedEvent event = objectMapper.readValue(payload, InventoryRejectedEvent.class);

            MDC.put("eventId", event.eventId().toString());
            MDC.put("orderId", event.orderId().toString());

            log.info("Received InventoryRejected event");

            notificationService.handleInventoryRejectedEvent(event);
        } catch (JacksonException e) {
            log.error("Error processing InventoryRejected event. key={}, payload={}, error={}", key, payload, e.getMessage(), e);
            throw new InvalidEventException("Failed to process InventoryRejected event", e);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove("eventId");
            MDC.remove("orderId");
        }
    }
}

