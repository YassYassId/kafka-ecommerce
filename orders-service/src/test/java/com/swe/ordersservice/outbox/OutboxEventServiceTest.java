package com.swe.ordersservice.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
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

    @Nested
    @DisplayName("markAsPublished")
    class MarkAsPublishedTests {

        @Test
        @DisplayName("should successfully mark outbox event as published when 1 row is updated")
        void shouldMarkEventAsPublishedSuccessfully() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            when(outboxEventRepository.markAsPublished(eventId)).thenReturn(1);

            // Act
            outboxEventService.markAsPublished(eventId);

            // Assert
            verify(outboxEventRepository).markAsPublished(eventId);
        }

        @Test
        @DisplayName("should throw IllegalStateException when no rows are updated")
        void shouldThrowExceptionWhenNoRowUpdated() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            when(outboxEventRepository.markAsPublished(eventId)).thenReturn(0);

            // Act & Assert
            assertThatThrownBy(() -> outboxEventService.markAsPublished(eventId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Failed to mark outbox event as published: " + eventId);

            verify(outboxEventRepository).markAsPublished(eventId);
        }
    }

    @Nested
    @DisplayName("claimEvents")
    class ClaimEventsTests {

        @Test
        @DisplayName("should claim events and set claimedUntil timestamp 30 seconds into future")
        void shouldClaimEventsAndSetClaimedUntil() {
            // Arrange
            UUID eventId1 = UUID.randomUUID();
            UUID eventId2 = UUID.randomUUID();

            OutboxEvent event1 = OutboxEvent.builder()
                    .id(eventId1)
                    .aggregateType("Order")
                    .aggregateId(UUID.randomUUID())
                    .eventType("OrderCreated")
                    .eventVersion(1)
                    .payload("{}")
                    .createdAt(OffsetDateTime.now())
                    .retryCount(0)
                    .build();

            OutboxEvent event2 = OutboxEvent.builder()
                    .id(eventId2)
                    .aggregateType("Order")
                    .aggregateId(UUID.randomUUID())
                    .eventType("OrderCreated")
                    .eventVersion(1)
                    .payload("{}")
                    .createdAt(OffsetDateTime.now())
                    .retryCount(0)
                    .build();

            List<OutboxEvent> claimableEvents = List.of(event1, event2);
            when(outboxEventRepository.findClaimableEvents()).thenReturn(claimableEvents);

            OffsetDateTime beforeClaim = OffsetDateTime.now().plusSeconds(29);

            // Act
            List<OutboxEvent> claimedEvents = outboxEventService.claimEvents();

            OffsetDateTime afterClaim = OffsetDateTime.now().plusSeconds(31);

            // Assert
            assertThat(claimedEvents).hasSize(2);
            assertThat(claimedEvents).containsExactly(event1, event2);

            for (OutboxEvent event : claimedEvents) {
                assertThat(event.getClaimedUntil()).isNotNull();
                assertThat(event.getClaimedUntil()).isAfter(beforeClaim);
                assertThat(event.getClaimedUntil()).isBefore(afterClaim);
            }

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.forClass(List.class);
            verify(outboxEventRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).containsExactly(event1, event2);
        }

        @Test
        @DisplayName("should return empty list when no claimable events exist")
        void shouldReturnEmptyListWhenNoEventsFound() {
            // Arrange
            when(outboxEventRepository.findClaimableEvents()).thenReturn(Collections.emptyList());

            // Act
            List<OutboxEvent> claimedEvents = outboxEventService.claimEvents();

            // Assert
            assertThat(claimedEvents).isEmpty();
            verify(outboxEventRepository).saveAll(Collections.emptyList());
        }
    }

    @Nested
    @DisplayName("recordFailure")
    class RecordFailureTests {

        @Test
        @DisplayName("should successfully record failure when 1 row is updated")
        void shouldRecordFailureSuccessfully() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            String errorMessage = "Kafka cluster unreachable";
            when(outboxEventRepository.recordFailure(eventId, errorMessage)).thenReturn(1);

            // Act
            outboxEventService.recordFailure(eventId, errorMessage);

            // Assert
            verify(outboxEventRepository).recordFailure(eventId, errorMessage);
        }

        @Test
        @DisplayName("should throw IllegalStateException when failure record update affects 0 rows")
        void shouldThrowExceptionWhenRecordFailureFails() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            String errorMessage = "Network timeout";
            when(outboxEventRepository.recordFailure(eventId, errorMessage)).thenReturn(0);

            // Act & Assert
            assertThatThrownBy(() -> outboxEventService.recordFailure(eventId, errorMessage))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Failed to record failure for outbox event: " + eventId);

            verify(outboxEventRepository).recordFailure(eventId, errorMessage);
        }
    }
}
