package com.swe.ordersservice.outbox;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.OffsetDateTime;
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
    @DisplayName("should save and retrieve outbox event with JSON payload and metadata")
    void shouldSaveAndRetrieveOutboxEvent() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String jsonPayload = "{\"eventId\": \"" + eventId + "\", \"orderId\": \"" + orderId + "\"}";
        OffsetDateTime now = OffsetDateTime.now();

        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(orderId)
                .eventType("OrderCreated")
                .eventVersion(1)
                .payload(jsonPayload)
                .createdAt(now)
                .retryCount(0)
                .build();

        // Act
        OutboxEvent savedEvent = outboxEventRepository.saveAndFlush(event);

        // Clear persistence context to force fresh database read
        entityManager.clear();

        // Assert
        OutboxEvent retrievedEvent = outboxEventRepository.findById(savedEvent.getId()).orElseThrow();
        assertThat(retrievedEvent.getId()).isEqualTo(eventId);
        assertThat(retrievedEvent.getAggregateType()).isEqualTo("Order");
        assertThat(retrievedEvent.getAggregateId()).isEqualTo(orderId);
        assertThat(retrievedEvent.getEventType()).isEqualTo("OrderCreated");
        assertThat(retrievedEvent.getEventVersion()).isEqualTo(1);
        assertThat(retrievedEvent.getPayload())
                .contains(eventId.toString())
                .contains(orderId.toString());
        assertThat(retrievedEvent.getCreatedAt()).isNotNull();
        assertThat(retrievedEvent.getPublishedAt()).isNull();
        assertThat(retrievedEvent.getRetryCount()).isZero();
        assertThat(retrievedEvent.getLastError()).isNull();
    }

    @Test
    @DisplayName("should allow updating published_at timestamp when event is marked as published")
    void shouldUpdatePublishedAtTimestamp() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("OrderCreated")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now())
                .retryCount(0)
                .build();

        outboxEventRepository.saveAndFlush(event);
        entityManager.clear();

        // Act
        OutboxEvent eventToUpdate = outboxEventRepository.findById(eventId).orElseThrow();
        OffsetDateTime publishedTime = OffsetDateTime.now();
        eventToUpdate.setPublishedAt(publishedTime);
        outboxEventRepository.saveAndFlush(eventToUpdate);
        entityManager.clear();

        // Assert
        OutboxEvent updatedEvent = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(updatedEvent.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("should update retry_count and last_error on outbox event failure")
    void shouldUpdateRetryCountAndLastError() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("OrderCreated")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now())
                .retryCount(0)
                .build();

        outboxEventRepository.saveAndFlush(event);
        entityManager.clear();

        // Act
        OutboxEvent eventToUpdate = outboxEventRepository.findById(eventId).orElseThrow();
        eventToUpdate.setRetryCount(eventToUpdate.getRetryCount() + 1);
        eventToUpdate.setLastError("Connection timeout to broker");
        outboxEventRepository.saveAndFlush(eventToUpdate);
        entityManager.clear();

        // Assert
        OutboxEvent updatedEvent = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(updatedEvent.getRetryCount()).isEqualTo(1);
        assertThat(updatedEvent.getLastError()).isEqualTo("Connection timeout to broker");
    }
}
