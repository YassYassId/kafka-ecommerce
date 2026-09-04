package com.swe.ordersservice.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void markAsPublished(UUID eventId) {
        int updated = outboxEventRepository.markAsPublished(eventId);

        if (updated != 1) {
            throw new IllegalStateException(
                    "Failed to mark outbox event as published: " + eventId
            );
        }
    }

    @Transactional
    public List<OutboxEvent> claimEvents() {

        List<OutboxEvent> events = outboxEventRepository.findClaimableEvents();
        OffsetDateTime claimUntil = OffsetDateTime.now().plusSeconds(30);

        for (OutboxEvent event : events) {
            event.setClaimedUntil(claimUntil);
        }
        outboxEventRepository.saveAll(events);
        return events;
    }

    @Transactional
    public void recordFailure(UUID eventId, String error) {

        int updated = outboxEventRepository.recordFailure(
                eventId,
                error
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Failed to record failure for outbox event: " + eventId
            );
        }
    }
}
