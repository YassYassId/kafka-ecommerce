package com.swe.catalogservice.outbox;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final int CLAIM_SECONDS = 30;

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

    @Transactional
    public List<OutboxEvent> claimEvents() {

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime claimedUntil = now.plusSeconds(CLAIM_SECONDS);

        List<OutboxEvent> candidates = outboxEventRepository.findAvailableEvents(now);

        List<OutboxEvent> claimedEvents = new ArrayList<>();

        for (OutboxEvent candidate : candidates) {

            int updated = outboxEventRepository.claimEvent(candidate.getId(), now, claimedUntil);

            if (updated == 1) {
                candidate.setClaimedUntil(claimedUntil);
                claimedEvents.add(candidate);
            }
        }

        return claimedEvents;
    }

    @Transactional
    public void markAsPublished(UUID eventId) {

        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow();

        event.setPublishedAt(OffsetDateTime.now());
        event.setClaimedUntil(null);
        event.setLastError(null);
    }

    @Transactional
    public void recordFailure(UUID eventId, String error) {

        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow();

        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastError(error);
        event.setClaimedUntil(null);
    }
}
