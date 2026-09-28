package com.swe.catalogservice.outbox;

import com.swe.catalogservice.observability.CatalogMetrics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private CatalogTopicResolver topicResolver;

    @Mock
    private CatalogMetrics catalogMetrics;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("should do nothing when no events are claimed")
    void publishPendingEvents_WhenNoEventsClaimed_ShouldDoNothing() {
        // Arrange
        when(outboxService.claimEvents()).thenReturn(Collections.emptyList());

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(outboxService).claimEvents();
        verifyNoInteractions(kafkaTemplate, topicResolver, catalogMetrics);
        verify(outboxService, never()).markAsPublished(any());
        verify(outboxService, never()).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("should publish claimed events with correlation ID header, mark as published, and record metric")
    @SuppressWarnings("unchecked")
    void publishPendingEvents_WhenEventsClaimedWithCorrelationId_ShouldPublishAndRecordMetric() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String payload = "{\"productId\":\"" + productId + "\"}";
        String eventType = "ProductCreated";
        String topic = "product.created";

        OutboxEvent event = createOutboxEvent(eventId, productId, eventType, payload, correlationId);

        when(outboxService.claimEvents()).thenReturn(List.of(event));
        when(topicResolver.resolve(eventType)).thenReturn(topic);

        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, String> capturedRecord = recordCaptor.getValue();
        assertThat(capturedRecord.topic()).isEqualTo(topic);
        assertThat(capturedRecord.key()).isEqualTo(productId.toString());
        assertThat(capturedRecord.value()).isEqualTo(payload);

        Header correlationHeader = capturedRecord.headers().lastHeader("X-Correlation-Id");
        assertThat(correlationHeader).isNotNull();
        assertThat(new String(correlationHeader.value(), StandardCharsets.UTF_8)).isEqualTo(correlationId);

        verify(outboxService).markAsPublished(eventId);
        verify(catalogMetrics).eventPublished(eventType);
        verify(outboxService, never()).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("should publish successfully without correlation header when correlationId is null")
    @SuppressWarnings("unchecked")
    void publishPendingEvents_WhenCorrelationIdIsNull_ShouldPublishWithoutHeader() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String payload = "{\"productId\":\"" + productId + "\"}";
        String eventType = "PriceChanged";
        String topic = "product.price-changed";

        OutboxEvent event = createOutboxEvent(eventId, productId, eventType, payload, null);

        when(outboxService.claimEvents()).thenReturn(List.of(event));
        when(topicResolver.resolve(eventType)).thenReturn(topic);

        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, String> capturedRecord = recordCaptor.getValue();
        assertThat(capturedRecord.headers().lastHeader("X-Correlation-Id")).isNull();

        verify(outboxService).markAsPublished(eventId);
        verify(catalogMetrics).eventPublished(eventType);
        verify(outboxService, never()).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("should record failure when Kafka sending throws exception")
    @SuppressWarnings("unchecked")
    void publishPendingEvents_WhenKafkaFails_ShouldRecordFailure() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String payload = "{\"productId\":\"" + productId + "\"}";
        String eventType = "ProductUpdated";
        String topic = "product.updated";

        OutboxEvent event = createOutboxEvent(eventId, productId, eventType, payload, "cid-123");

        when(outboxService.claimEvents()).thenReturn(List.of(event));
        when(topicResolver.resolve(eventType)).thenReturn(topic);

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka Broker Unavailable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(outboxService).recordFailure(eq(eventId), eq("java.lang.RuntimeException: Kafka Broker Unavailable"));
        verify(outboxService, never()).markAsPublished(eventId);
        verify(catalogMetrics, never()).eventPublished(anyString());
    }

    @Test
    @DisplayName("should record simple class name when exception message is null")
    @SuppressWarnings("unchecked")
    void publishPendingEvents_WhenExceptionMessageIsNull_ShouldUseSimpleClassName() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String payload = "{\"productId\":\"" + productId + "\"}";
        String eventType = "ProductRetired";

        OutboxEvent event = createOutboxEvent(eventId, productId, eventType, payload, null);

        when(outboxService.claimEvents()).thenReturn(List.of(event));
        when(topicResolver.resolve(eventType)).thenThrow(new NullPointerException());

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(outboxService).recordFailure(eventId, "NullPointerException");
        verify(outboxService, never()).markAsPublished(eventId);
        verify(catalogMetrics, never()).eventPublished(anyString());
    }

    @Test
    @DisplayName("should continue publishing remaining events when one event in batch fails")
    @SuppressWarnings("unchecked")
    void publishPendingEvents_WhenOneEventFails_ShouldContinueBatch() {
        // Arrange
        UUID eventId1 = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        OutboxEvent event1 = createOutboxEvent(eventId1, productId1, "ProductCreated", "{}", "cid-1");

        UUID eventId2 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        OutboxEvent event2 = createOutboxEvent(eventId2, productId2, "ProductUpdated", "{}", "cid-2");

        when(outboxService.claimEvents()).thenReturn(List.of(event1, event2));
        when(topicResolver.resolve("ProductCreated")).thenThrow(new RuntimeException("Resolver error"));
        when(topicResolver.resolve("ProductUpdated")).thenReturn("product.updated");

        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // Act
        outboxPublisher.publishPendingEvents();

        // Assert
        verify(outboxService).recordFailure(eventId1, "Resolver error");
        verify(outboxService, never()).markAsPublished(eventId1);

        verify(outboxService).markAsPublished(eventId2);
        verify(catalogMetrics).eventPublished("ProductUpdated");
    }

    private OutboxEvent createOutboxEvent(UUID eventId, UUID productId, String type, String payload, String correlationId) {
        return OutboxEvent.builder()
                .id(eventId)
                .aggregateId(productId)
                .type(type)
                .payload(payload)
                .correlationId(correlationId)
                .occurredAt(OffsetDateTime.now())
                .build();
    }
}
