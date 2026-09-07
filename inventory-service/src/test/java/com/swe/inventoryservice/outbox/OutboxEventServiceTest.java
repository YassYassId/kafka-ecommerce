package com.swe.inventoryservice.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxEventService outboxEventService;

    @Test
    @DisplayName("should claim events and set claimedUntil timestamp")
    void shouldClaimEvents() {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("InventoryReserved")
                .eventVersion(1)
                .payload("{}")
                .createdAt(OffsetDateTime.now())
                .build();

        when(outboxEventRepository.findClaimableEvents()).thenReturn(List.of(event));

        List<OutboxEvent> claimed = outboxEventService.claimEvents();

        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().getClaimedUntil()).isNotNull();
        verify(outboxEventRepository).saveAll(claimed);
    }

    @Test
    @DisplayName("should mark event as published")
    void shouldMarkAsPublished() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.markAsPublished(eventId)).thenReturn(1);

        outboxEventService.markAsPublished(eventId);

        verify(outboxEventRepository).markAsPublished(eventId);
    }

    @Test
    @DisplayName("should throw when markAsPublished updates 0 rows")
    void shouldThrowWhenMarkAsPublishedFails() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.markAsPublished(eventId)).thenReturn(0);

        assertThatThrownBy(() -> outboxEventService.markAsPublished(eventId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to mark outbox event as published");
    }

    @Test
    @DisplayName("should record failure for event")
    void shouldRecordFailure() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.recordFailure(eventId, "Connection error")).thenReturn(1);

        outboxEventService.recordFailure(eventId, "Connection error");

        verify(outboxEventRepository).recordFailure(eventId, "Connection error");
    }

    @Test
    @DisplayName("should throw when recordFailure updates 0 rows")
    void shouldThrowWhenRecordFailureFails() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.recordFailure(eventId, "Connection error")).thenReturn(0);

        assertThatThrownBy(() -> outboxEventService.recordFailure(eventId, "Connection error"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to record failure for outbox event");
    }
}
