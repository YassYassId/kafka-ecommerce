package com.swe.catalogservice.outbox;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void saveEvent(UUID eventId, UUID aggregateId, String eventType, OffsetDateTime occurredAt, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(eventId)
                    .aggregateType("PRODUCT")
                    .aggregateId(aggregateId)
                    .type(eventType)
                    .payload(payload)
                    .occurredAt(occurredAt)
                    .correlationId(MDC.get("correlationId"))
                    .build();

            outboxEventRepository.save(outboxEvent);

        } catch (JacksonException ex) {
            throw new IllegalStateException(
                    "Failed to serialize outbox event: " + eventType,
                    ex
            );
        }
    }
}
