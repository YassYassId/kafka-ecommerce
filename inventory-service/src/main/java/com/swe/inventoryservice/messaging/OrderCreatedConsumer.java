package com.swe.inventoryservice.messaging;

import com.swe.inventoryservice.event.OrderCreatedEvent;
import com.swe.inventoryservice.exception.InvalidEventException;
import com.swe.inventoryservice.metrics.KafkaMetrics;
import com.swe.inventoryservice.service.InventoryService;
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
public class OrderCreatedConsumer {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final KafkaMetrics kafkaMetrics;

    @KafkaListener(topics = "order.created", groupId = "inventory-service")
    public void consume(@Header(KafkaHeaders.RECEIVED_KEY) String key,
                        @Payload String payload,
                        @Header(name = CORRELATION_HEADER, required = false) String correlationId) {

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();

            log.warn("Received OrderCreated event without correlation ID. Generated fallback correlationId");
        }
        long start = System.nanoTime();
        String outcome = "success";

        try {
            MDC.put(MDC_KEY, correlationId);

            OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);

            MDC.put("eventId", event.eventId().toString());
            MDC.put("orderId", event.orderId().toString());

            log.info("Received OrderCreated event");

            inventoryService.processOrder(event);
        } catch (JacksonException e) {
            outcome = "failure";
            log.error(
                    "Failed to deserialize OrderCreated event. payload={}",
                    payload,
                    e
            );
            throw new InvalidEventException("Failed to deserialize OrderCreated event", e);
        } finally {
            kafkaMetrics.recordProcessing("OrderCreated", outcome, System.nanoTime() - start);
            MDC.remove(MDC_KEY);
            MDC.remove("eventId");
            MDC.remove("orderId");
        }
    }
}
