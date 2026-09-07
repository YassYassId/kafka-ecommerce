package com.swe.inventoryservice.outbox;

import com.swe.inventoryservice.config.KafkaTopicConfig;
import com.swe.inventoryservice.messaging.InventoryEventProducer;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private InventoryEventProducer inventoryEventProducer;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("should do nothing when no events are claimed")
    void shouldDoNothingWhenNoEventsClaimed() {
        when(outboxEventService.claimEvents()).thenReturn(Collections.emptyList());

        outboxPublisher.publishPendingEvents();

        verify(outboxEventService).claimEvents();
        verifyNoInteractions(inventoryEventProducer);
        verify(outboxEventService, never()).markAsPublished(any());
        verify(outboxEventService, never()).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("should publish InventoryReserved event to inventory.reserved topic and mark as published")
    void shouldPublishInventoryReservedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\"}";

        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(orderId)
                .eventType("InventoryReserved")
                .eventVersion(1)
                .payload(payload)
                .createdAt(OffsetDateTime.now())
                .retryCount(0)
                .build();

        when(outboxEventService.claimEvents()).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(inventoryEventProducer.publishInventoryEvent(KafkaTopicConfig.INVENTORY_RESERVED_TOPIC, orderId.toString(), payload))
                .thenReturn(future);

        outboxPublisher.publishPendingEvents();

        verify(inventoryEventProducer).publishInventoryEvent(KafkaTopicConfig.INVENTORY_RESERVED_TOPIC, orderId.toString(), payload);
        verify(outboxEventService).markAsPublished(eventId);
        verify(outboxEventService, never()).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("should publish InventoryRejected event to inventory.rejected topic and mark as published")
    void shouldPublishInventoryRejectedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\",\"reason\":\"INSUFFICIENT_STOCK\"}";

        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(orderId)
                .eventType("InventoryRejected")
                .eventVersion(1)
                .payload(payload)
                .createdAt(OffsetDateTime.now())
                .retryCount(0)
                .build();

        when(outboxEventService.claimEvents()).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(inventoryEventProducer.publishInventoryEvent(KafkaTopicConfig.INVENTORY_REJECTED_TOPIC, orderId.toString(), payload))
                .thenReturn(future);

        outboxPublisher.publishPendingEvents();

        verify(inventoryEventProducer).publishInventoryEvent(KafkaTopicConfig.INVENTORY_REJECTED_TOPIC, orderId.toString(), payload);
        verify(outboxEventService).markAsPublished(eventId);
    }

    @Test
    @DisplayName("should record failure when publish fails")
    void shouldRecordFailureWhenPublishFails() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\"}";

        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("Order")
                .aggregateId(orderId)
                .eventType("InventoryReserved")
                .eventVersion(1)
                .payload(payload)
                .createdAt(OffsetDateTime.now())
                .retryCount(0)
                .build();

        when(outboxEventService.claimEvents()).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unreachable"));
        when(inventoryEventProducer.publishInventoryEvent(KafkaTopicConfig.INVENTORY_RESERVED_TOPIC, orderId.toString(), payload))
                .thenReturn(failedFuture);

        outboxPublisher.publishPendingEvents();

        verify(outboxEventService, never()).markAsPublished(eventId);
        verify(outboxEventService).recordFailure(eq(eventId), contains("Kafka unreachable"));
    }
}
