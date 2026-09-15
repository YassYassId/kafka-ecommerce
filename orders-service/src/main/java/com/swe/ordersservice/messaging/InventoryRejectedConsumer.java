package com.swe.ordersservice.messaging;

import com.swe.ordersservice.event.InventoryRejectedEvent;
import com.swe.ordersservice.exception.InvalidEventException;
import com.swe.ordersservice.metrics.KafkaMetrics;
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
public class InventoryRejectedConsumer {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final KafkaMetrics kafkaMetrics;

    @KafkaListener(topics = "inventory.rejected", groupId = "orders-service")
    public void consume(@Header(KafkaHeaders.RECEIVED_KEY) String key,
                        @Payload String payload,
                        @Header(name = CORRELATION_HEADER, required = false) String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();

            log.warn("Received InventoryReserved event without correlation ID. Generated fallback correlationId");
        }
        long start = System.nanoTime();
        String outcome = "success";

        try {
            MDC.put(MDC_KEY, correlationId);

            InventoryRejectedEvent event = objectMapper.readValue(payload, InventoryRejectedEvent.class);

            MDC.put("eventId", event.eventId().toString());
            MDC.put("orderId", event.orderId().toString());

            log.info("Received InventoryRejected event");

            orderService.cancelOrder(event);
        } catch (JacksonException e) {
            outcome = "failure";
            log.error("Error occurred while processing InventoryRejected event", e);
            throw new InvalidEventException("Failed to deserialize InventoryRejected event", e);
        } finally {
            kafkaMetrics.recordProcessing("InventoryRejected", outcome, System.nanoTime() - start);
            MDC.remove(MDC_KEY);
            MDC.remove("eventId");
            MDC.remove("orderId");
        }
    }
}

