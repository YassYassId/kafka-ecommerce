package com.swe.inventoryservice.outbox;

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
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String jsonPayload = "{\"eventId\": \"" + eventId + "\", \"orderId\": \"" + orderId + "\"}";
        OffsetDateTime now = OffsetDateTime.now();

        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(orderId)
                .eventType("InventoryReserved")
                .eventVersion(1)
                .payload(jsonPayload)
                .createdAt(now)
                .retryCount(0)
                .build();

        OutboxEvent savedEvent = outboxEventRepository.saveAndFlush(event);
        entityManager.clear();

        OutboxEvent retrievedEvent = outboxEventRepository.findById(savedEvent.getId()).orElseThrow();
        assertThat(retrievedEvent.getId()).isEqualTo(eventId);
        assertThat(retrievedEvent.getAggregateType()).isEqualTo("Order");
        assertThat(retrievedEvent.getAggregateId()).isEqualTo(orderId);
        assertThat(retrievedEvent.getEventType()).isEqualTo("InventoryReserved");
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
    @DisplayName("should mark outbox event as published using custom repository modifying query")
    void shouldMarkAsPublishedUsingCustomQuery() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("InventoryReserved")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now())
                .retryCount(0)
                .build();

        outboxEventRepository.saveAndFlush(event);
        entityManager.clear();

        int updatedRows = outboxEventRepository.markAsPublished(eventId);
        entityManager.clear();

        assertThat(updatedRows).isEqualTo(1);
        OutboxEvent updatedEvent = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(updatedEvent.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("should record failure and increment retry_count using custom repository modifying query")
    void shouldRecordFailureUsingCustomQuery() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("InventoryReserved")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now())
                .retryCount(0)
                .build();

        outboxEventRepository.saveAndFlush(event);
        entityManager.clear();

        int updatedRows = outboxEventRepository.recordFailure(eventId, "Broker disconnected");
        entityManager.clear();

        assertThat(updatedRows).isEqualTo(1);
        OutboxEvent updatedEvent = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(updatedEvent.getRetryCount()).isEqualTo(1);
        assertThat(updatedEvent.getLastError()).isEqualTo("Broker disconnected");
    }

    @Test
    @DisplayName("should find claimable events and skip published or active leases")
    void shouldFindClaimableEventsCorrectly() {
        UUID claimableId1 = UUID.randomUUID();
        UUID claimableId2 = UUID.randomUUID();
        UUID publishedId = UUID.randomUUID();
        UUID lockedId = UUID.randomUUID();

        OutboxEvent event1 = OutboxEvent.builder()
                .id(claimableId1)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("InventoryReserved")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .retryCount(0)
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .id(claimableId2)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("InventoryReserved")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now().minusMinutes(4))
                .claimedUntil(OffsetDateTime.now().minusSeconds(10))
                .retryCount(1)
                .build();

        OutboxEvent event3 = OutboxEvent.builder()
                .id(publishedId)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("InventoryReserved")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now().minusMinutes(3))
                .publishedAt(OffsetDateTime.now())
                .retryCount(0)
                .build();

        OutboxEvent event4 = OutboxEvent.builder()
                .id(lockedId)
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("InventoryReserved")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now().minusMinutes(2))
                .claimedUntil(OffsetDateTime.now().plusSeconds(30))
                .retryCount(0)
                .build();

        outboxEventRepository.saveAllAndFlush(List.of(event1, event2, event3, event4));
        entityManager.clear();

        List<OutboxEvent> claimableEvents = outboxEventRepository.findClaimableEvents();

        List<UUID> claimableIds = claimableEvents.stream().map(OutboxEvent::getId).toList();
        assertThat(claimableIds).contains(claimableId1, claimableId2);
        assertThat(claimableIds).doesNotContain(publishedId, lockedId);
    }
}
