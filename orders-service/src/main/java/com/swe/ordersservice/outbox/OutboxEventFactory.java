package com.swe.ordersservice.outbox;

import com.swe.ordersservice.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEvent create(OrderCreatedEvent event) {

        try {
            return OutboxEvent.builder()
                    .id(event.eventId())
                    .aggregateType("Order")
                    .aggregateId(event.orderId())
                    .eventType("OrderCreated")
                    .eventVersion(event.version())
                    .payload(objectMapper.writeValueAsString(event))
                    .createdAt(OffsetDateTime.now())
                    .retryCount(0)
                    .build();

        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Failed to serialize OrderCreatedEvent",
                    e
            );
        }
    }
}