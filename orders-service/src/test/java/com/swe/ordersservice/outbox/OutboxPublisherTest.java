package com.swe.ordersservice.outbox;

import com.swe.ordersservice.messaging.OrderEventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OrderEventProducer orderEventProducer;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("should do nothing when no events are claimed")
    void publishPendingEvents_WhenNoEventsClaimed_ShouldDoNothing() {
        // Arrange
        when(outboxEventService.claimEvents()).thenReturn(Collections.emptyList());

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(outboxEventService).claimEvents();
        verifyNoInteractions(orderEventProducer);
        verify(outboxEventService, never()).markAsPublished(any());
        verify(outboxEventService, never()).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("should publish all claimed events and mark them as published on success")
    void publishPendingEvents_WhenEventsClaimed_ShouldPublishAndMarkPublished() {
        // Arrange
        UUID eventId1 = UUID.randomUUID();
        UUID orderId1 = UUID.randomUUID();
        String payload1 = "{\"orderId\":\"" + orderId1 + "\"}";

        UUID eventId2 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        String payload2 = "{\"orderId\":\"" + orderId2 + "\"}";

        OutboxEvent event1 = createOutboxEvent(eventId1, orderId1, payload1);
        OutboxEvent event2 = createOutboxEvent(eventId2, orderId2, payload2);

        when(outboxEventService.claimEvents()).thenReturn(List.of(event1, event2));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(orderEventProducer.publishOrderCreatedEvent(orderId1.toString(), payload1)).thenReturn(future);
        when(orderEventProducer.publishOrderCreatedEvent(orderId2.toString(), payload2)).thenReturn(future);

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(orderEventProducer).publishOrderCreatedEvent(orderId1.toString(), payload1);
        verify(orderEventProducer).publishOrderCreatedEvent(orderId2.toString(), payload2);
        verify(outboxEventService).markAsPublished(eventId1);
        verify(outboxEventService).markAsPublished(eventId2);
        verify(outboxEventService, never()).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("should record failure when Kafka publishing throws an exception")
    void publishPendingEvents_WhenPublishingFails_ShouldRecordFailure() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\"}";

        OutboxEvent event = createOutboxEvent(eventId, orderId, payload);
        when(outboxEventService.claimEvents()).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka Broker Unavailable"));
        when(orderEventProducer.publishOrderCreatedEvent(orderId.toString(), payload)).thenReturn(failedFuture);

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(orderEventProducer).publishOrderCreatedEvent(orderId.toString(), payload);
        verify(outboxEventService, never()).markAsPublished(eventId);
        verify(outboxEventService).recordFailure(eq(eventId), contains("Kafka Broker Unavailable"));
    }

    @Test
    @DisplayName("should use exception simple class name when error message is null")
    void publishPendingEvents_WhenExceptionMessageIsNull_ShouldUseSimpleClassName() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\"}";

        OutboxEvent event = createOutboxEvent(eventId, orderId, payload);
        when(outboxEventService.claimEvents()).thenReturn(List.of(event));

        when(orderEventProducer.publishOrderCreatedEvent(orderId.toString(), payload))
                .thenThrow(new NullPointerException());

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(outboxEventService).recordFailure(eq(eventId), eq("NullPointerException"));
    }

    @Test
    @DisplayName("should handle exception gracefully when recordFailure itself throws without aborting other events")
    void publishPendingEvents_WhenRecordFailureThrows_ShouldCatchAndContinueBatch() {
        // Arrange
        UUID eventId1 = UUID.randomUUID();
        UUID orderId1 = UUID.randomUUID();
        String payload1 = "{\"orderId\":\"" + orderId1 + "\"}";

        UUID eventId2 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        String payload2 = "{\"orderId\":\"" + orderId2 + "\"}";

        OutboxEvent event1 = createOutboxEvent(eventId1, orderId1, payload1);
        OutboxEvent event2 = createOutboxEvent(eventId2, orderId2, payload2);

        when(outboxEventService.claimEvents()).thenReturn(List.of(event1, event2));

        // Event 1 fails publishing AND fails recordFailure
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(orderEventProducer.publishOrderCreatedEvent(orderId1.toString(), payload1)).thenReturn(failedFuture);
        doThrow(new RuntimeException("Database error during recordFailure"))
                .when(outboxEventService).recordFailure(eq(eventId1), anyString());

        // Event 2 succeeds
        CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(mock(SendResult.class));
        when(orderEventProducer.publishOrderCreatedEvent(orderId2.toString(), payload2)).thenReturn(successFuture);

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(orderEventProducer).publishOrderCreatedEvent(orderId1.toString(), payload1);
        verify(orderEventProducer).publishOrderCreatedEvent(orderId2.toString(), payload2);
        verify(outboxEventService).recordFailure(eq(eventId1), contains("Kafka down"));
        verify(outboxEventService).markAsPublished(eventId2);
    }

    private OutboxEvent createOutboxEvent(UUID eventId, UUID orderId, String payload) {
        return OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(orderId)
                .eventType("OrderCreated")
                .eventVersion(1)
                .payload(payload)
                .createdAt(OffsetDateTime.now())
                .retryCount(0)
                .build();
    }
}
