package com.swe.ordersservice.outbox;

import com.swe.ordersservice.messaging.OrderEventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.support.SendResult;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("should publish all claimed events with correlationId, set MDC, and mark them as published on success")
    void publishPendingEvents_WhenEventsClaimed_ShouldPublishAndMarkPublished() {
        // Arrange
        UUID eventId1 = UUID.randomUUID();
        UUID orderId1 = UUID.randomUUID();
        String payload1 = "{\"orderId\":\"" + orderId1 + "\"}";
        String correlationId1 = UUID.randomUUID().toString();

        UUID eventId2 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        String payload2 = "{\"orderId\":\"" + orderId2 + "\"}";
        String correlationId2 = UUID.randomUUID().toString();

        OutboxEvent event1 = createOutboxEvent(eventId1, orderId1, payload1, correlationId1);
        OutboxEvent event2 = createOutboxEvent(eventId2, orderId2, payload2, correlationId2);

        when(outboxEventService.claimEvents()).thenReturn(List.of(event1, event2));

        AtomicReference<String> mdcDuringEvent1 = new AtomicReference<>();
        when(orderEventProducer.publishOrderCreatedEvent(orderId1.toString(), payload1, correlationId1))
                .thenAnswer(inv -> {
                    mdcDuringEvent1.set(MDC.get("correlationId"));
                    return CompletableFuture.completedFuture(mock(SendResult.class));
                });

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(orderEventProducer.publishOrderCreatedEvent(orderId2.toString(), payload2, correlationId2)).thenReturn(future);

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        assertThat(mdcDuringEvent1.get()).isEqualTo(correlationId1);
        assertThat(MDC.get("correlationId")).isNull();

        verify(orderEventProducer).publishOrderCreatedEvent(orderId1.toString(), payload1, correlationId1);
        verify(orderEventProducer).publishOrderCreatedEvent(orderId2.toString(), payload2, correlationId2);
        verify(outboxEventService).markAsPublished(eventId1);
        verify(outboxEventService).markAsPublished(eventId2);
        verify(outboxEventService, never()).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("should record failure when Kafka publishing throws an exception and clean MDC")
    void publishPendingEvents_WhenPublishingFails_ShouldRecordFailure() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\"}";
        String correlationId = UUID.randomUUID().toString();

        OutboxEvent event = createOutboxEvent(eventId, orderId, payload, correlationId);
        when(outboxEventService.claimEvents()).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka Broker Unavailable"));
        when(orderEventProducer.publishOrderCreatedEvent(orderId.toString(), payload, correlationId)).thenReturn(failedFuture);

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        assertThat(MDC.get("correlationId")).isNull();
        verify(orderEventProducer).publishOrderCreatedEvent(orderId.toString(), payload, correlationId);
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
        String correlationId = UUID.randomUUID().toString();

        OutboxEvent event = createOutboxEvent(eventId, orderId, payload, correlationId);
        when(outboxEventService.claimEvents()).thenReturn(List.of(event));

        when(orderEventProducer.publishOrderCreatedEvent(orderId.toString(), payload, correlationId))
                .thenThrow(new NullPointerException());

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        assertThat(MDC.get("correlationId")).isNull();
        verify(outboxEventService).recordFailure(eq(eventId), eq("NullPointerException"));
    }

    @Test
    @DisplayName("should handle exception gracefully when recordFailure itself throws without aborting other events")
    void publishPendingEvents_WhenRecordFailureThrows_ShouldCatchAndContinueBatch() {
        // Arrange
        UUID eventId1 = UUID.randomUUID();
        UUID orderId1 = UUID.randomUUID();
        String payload1 = "{\"orderId\":\"" + orderId1 + "\"}";
        String correlationId1 = UUID.randomUUID().toString();

        UUID eventId2 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        String payload2 = "{\"orderId\":\"" + orderId2 + "\"}";
        String correlationId2 = UUID.randomUUID().toString();

        OutboxEvent event1 = createOutboxEvent(eventId1, orderId1, payload1, correlationId1);
        OutboxEvent event2 = createOutboxEvent(eventId2, orderId2, payload2, correlationId2);

        when(outboxEventService.claimEvents()).thenReturn(List.of(event1, event2));

        // Event 1 fails publishing AND fails recordFailure
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(orderEventProducer.publishOrderCreatedEvent(orderId1.toString(), payload1, correlationId1)).thenReturn(failedFuture);
        doThrow(new RuntimeException("Database error during recordFailure"))
                .when(outboxEventService).recordFailure(eq(eventId1), anyString());

        // Event 2 succeeds
        CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(mock(SendResult.class));
        when(orderEventProducer.publishOrderCreatedEvent(orderId2.toString(), payload2, correlationId2)).thenReturn(successFuture);

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        assertThat(MDC.get("correlationId")).isNull();
        verify(orderEventProducer).publishOrderCreatedEvent(orderId1.toString(), payload1, correlationId1);
        verify(orderEventProducer).publishOrderCreatedEvent(orderId2.toString(), payload2, correlationId2);
        verify(outboxEventService).recordFailure(eq(eventId1), contains("Kafka down"));
        verify(outboxEventService).markAsPublished(eventId2);
    }

    private OutboxEvent createOutboxEvent(UUID eventId, UUID orderId, String payload, String correlationId) {
        return OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(orderId)
                .eventType("OrderCreated")
                .eventVersion(1)
                .correlationId(correlationId)
                .payload(payload)
                .createdAt(OffsetDateTime.now())
                .retryCount(0)
                .build();
    }
}
