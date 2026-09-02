package com.swe.ordersservice.outbox;

import com.swe.ordersservice.event.OrderCreatedEvent;
import com.swe.ordersservice.event.OrderCreatedItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventFactoryTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventFactory outboxEventFactory;

    @Test
    @DisplayName("should successfully build OutboxEvent from OrderCreatedEvent")
    void create_WhenValidEvent_ShouldReturnOutboxEvent() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId,
                orderId,
                customerId,
                List.of(new OrderCreatedItem(productId, 3)),
                Instant.now(),
                1
        );

        String expectedJsonPayload = "{\"eventId\":\"" + eventId + "\"}";
        when(objectMapper.writeValueAsString(event)).thenReturn(expectedJsonPayload);

        // Act
        OutboxEvent outboxEvent = outboxEventFactory.create(event);

        // Assert
        assertThat(outboxEvent).isNotNull();
        assertThat(outboxEvent.getId()).isEqualTo(eventId);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("Order");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(orderId);
        assertThat(outboxEvent.getEventType()).isEqualTo("OrderCreated");
        assertThat(outboxEvent.getEventVersion()).isEqualTo(1);
        assertThat(outboxEvent.getPayload()).isEqualTo(expectedJsonPayload);
        assertThat(outboxEvent.getCreatedAt()).isNotNull();
        assertThat(outboxEvent.getPublishedAt()).isNull();
        assertThat(outboxEvent.getRetryCount()).isZero();
        assertThat(outboxEvent.getLastError()).isNull();
    }

    @Test
    @DisplayName("should throw IllegalStateException when JSON serialization fails")
    void create_WhenSerializationFails_ShouldThrowIllegalStateException() {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new OrderCreatedItem(UUID.randomUUID(), 1)),
                Instant.now(),
                1
        );

        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JacksonException("Serialization failed") {});

        // Act & Assert
        assertThatThrownBy(() -> outboxEventFactory.create(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize OrderCreatedEvent")
                .hasCauseInstanceOf(JacksonException.class);
    }
}
