package com.swe.catalogservice.outbox;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("should save and retrieve outbox event with all fields")
    void shouldSaveAndRetrieveOutboxEvent() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String payload = "{\"sku\":\"SKU-TEST-01\",\"price\":29.99}";
        String correlationId = UUID.randomUUID().toString();

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("PRODUCT")
                .aggregateId(aggregateId)
                .type("ProductCreated")
                .payload(payload)
                .occurredAt(occurredAt)
                .correlationId(correlationId)
                .retryCount(0)
                .build();

        // Act
        OutboxEvent saved = outboxEventRepository.saveAndFlush(outboxEvent);

        entityManager.clear();

        // Assert
        Optional<OutboxEvent> found = outboxEventRepository.findById(saved.getId());
        assertThat(found).isPresent();
        OutboxEvent retrieved = found.get();
        assertThat(retrieved.getId()).isEqualTo(eventId);
        assertThat(retrieved.getAggregateType()).isEqualTo("PRODUCT");
        assertThat(retrieved.getAggregateId()).isEqualTo(aggregateId);
        assertThat(retrieved.getType()).isEqualTo("ProductCreated");
        assertThat(retrieved.getPayload()).isEqualTo(payload);
        assertThat(retrieved.getOccurredAt()).isNotNull();
        assertThat(retrieved.getCorrelationId()).isEqualTo(correlationId);
        assertThat(retrieved.getRetryCount()).isZero();
        assertThat(retrieved.getPublishedAt()).isNull();
        assertThat(retrieved.getClaimedUntil()).isNull();
        assertThat(retrieved.getLastError()).isNull();
    }

    @Test
    @DisplayName("should update published_at, retry_count, and last_error")
    void shouldUpdateOutboxEventPublishingFields() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("PRODUCT")
                .aggregateId(UUID.randomUUID())
                .type("ProductCreated")
                .payload("{}")
                .occurredAt(OffsetDateTime.now())
                .build();

        outboxEventRepository.saveAndFlush(outboxEvent);
        entityManager.clear();

        // Act
        OutboxEvent eventToUpdate = outboxEventRepository.findById(eventId).orElseThrow();
        OffsetDateTime publishedAt = OffsetDateTime.now();
        eventToUpdate.setPublishedAt(publishedAt);
        eventToUpdate.setRetryCount(2);
        eventToUpdate.setLastError("Failed to send Kafka message");
        eventToUpdate.setClaimedUntil(OffsetDateTime.now().plusSeconds(30));

        outboxEventRepository.saveAndFlush(eventToUpdate);
        entityManager.clear();

        // Assert
        OutboxEvent updated = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(updated.getPublishedAt()).isNotNull();
        assertThat(updated.getRetryCount()).isEqualTo(2);
        assertThat(updated.getLastError()).isEqualTo("Failed to send Kafka message");
        assertThat(updated.getClaimedUntil()).isNotNull();
    }
}
