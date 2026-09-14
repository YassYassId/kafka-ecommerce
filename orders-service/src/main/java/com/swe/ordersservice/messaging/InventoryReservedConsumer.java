package com.swe.ordersservice.messaging;

import com.swe.ordersservice.event.InventoryReservedEvent;
import com.swe.ordersservice.exception.InvalidEventException;
import com.swe.ordersservice.service.OrderService;
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
public class InventoryReservedConsumer {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory.reserved", groupId = "orders-service")
    public void consume(@Header(value = KafkaHeaders.RECEIVED_KEY) String key,
                        @Payload String payload,
                        @Header(name = CORRELATION_HEADER, required = false) String correlationId) {

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();

            log.warn(
                    "Received InventoryReserved event without correlation ID. Generated fallback correlationId={}",
                    correlationId
            );
        }

        try {

            MDC.put(MDC_KEY, correlationId);
            InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);

            log.info("Received InventoryReserved event. eventId={}, orderId={}, key={}", event.eventId(), event.orderId(), key);

            orderService.confirmOrder(event);
        } catch (JacksonException e) {
            log.error("Failed to deserialize InventoryReserved event. payload={}", payload, e);
            throw new InvalidEventException("Failed to deserialize InventoryReserved event", e);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}

