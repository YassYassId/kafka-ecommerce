package com.swe.ordersservice.outbox;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.OffsetDateTime;
import java.util.List;
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

    @Test
    @DisplayName("should mark outbox event as published using custom repository modifying query")
    void shouldMarkAsPublishedUsingCustomQuery() {
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
        int updatedRows = outboxEventRepository.markAsPublished(eventId);
        entityManager.clear();

        // Assert
        assertThat(updatedRows).isEqualTo(1);
        OutboxEvent updatedEvent = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(updatedEvent.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("should record failure and increment retry_count using custom repository modifying query")
    void shouldRecordFailureUsingCustomQuery() {
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
        int updatedRows = outboxEventRepository.recordFailure(eventId, "Broker disconnected");
        entityManager.clear();

        // Assert
        assertThat(updatedRows).isEqualTo(1);
        OutboxEvent updatedEvent = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(updatedEvent.getRetryCount()).isEqualTo(1);
        assertThat(updatedEvent.getLastError()).isEqualTo("Broker disconnected");
    }

    @Test
    @DisplayName("should find claimable events and skip published or active leases")
    void shouldFindClaimableEventsCorrectly() {
        // Arrange
        UUID claimableId1 = UUID.randomUUID();
        UUID claimableId2 = UUID.randomUUID();
        UUID publishedId = UUID.randomUUID();
        UUID lockedId = UUID.randomUUID();

        // Event 1: Unclaimed & unpublished -> claimable
        OutboxEvent event1 = OutboxEvent.builder()
                .id(claimableId1)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("OrderCreated")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .retryCount(0)
                .build();

        // Event 2: Expired lease & unpublished -> claimable
        OutboxEvent event2 = OutboxEvent.builder()
                .id(claimableId2)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("OrderCreated")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now().minusMinutes(4))
                .claimedUntil(OffsetDateTime.now().minusSeconds(10))
                .retryCount(1)
                .build();

        // Event 3: Already published -> NOT claimable
        OutboxEvent event3 = OutboxEvent.builder()
                .id(publishedId)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("OrderCreated")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now().minusMinutes(3))
                .publishedAt(OffsetDateTime.now())
                .retryCount(0)
                .build();

        // Event 4: Currently locked (lease in future) -> NOT claimable
        OutboxEvent event4 = OutboxEvent.builder()
                .id(lockedId)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("OrderCreated")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now().minusMinutes(2))
                .claimedUntil(OffsetDateTime.now().plusSeconds(30))
                .retryCount(0)
                .build();

        outboxEventRepository.saveAllAndFlush(List.of(event1, event2, event3, event4));
        entityManager.clear();

        // Act
        List<OutboxEvent> claimableEvents = outboxEventRepository.findClaimableEvents();

        // Assert
        List<UUID> claimableIds = claimableEvents.stream().map(OutboxEvent::getId).toList();
        assertThat(claimableIds).contains(claimableId1, claimableId2);
        assertThat(claimableIds).doesNotContain(publishedId, lockedId);
    }
}
