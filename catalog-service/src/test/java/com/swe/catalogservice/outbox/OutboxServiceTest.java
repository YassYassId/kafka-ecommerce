package com.swe.catalogservice.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("should serialize event and persist OutboxEvent with correlationId from MDC")
    void saveEvent_WhenSuccessfulWithCorrelationId_ShouldSaveOutboxEvent() throws Exception {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String eventType = "ProductCreated";
        OffsetDateTime occurredAt = OffsetDateTime.now();
        Object eventPayloadObj = new Object();
        String jsonPayload = "{\"id\":\"" + productId + "\"}";
        String correlationId = "corr-" + UUID.randomUUID();

        MDC.put("correlationId", correlationId);
        when(objectMapper.writeValueAsString(eventPayloadObj)).thenReturn(jsonPayload);

        // Act
        outboxService.saveEvent(eventId, productId, eventType, occurredAt, eventPayloadObj);

        // Assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getId()).isEqualTo(eventId);
        assertThat(savedEvent.getAggregateType()).isEqualTo("PRODUCT");
        assertThat(savedEvent.getAggregateId()).isEqualTo(productId);
        assertThat(savedEvent.getType()).isEqualTo(eventType);
        assertThat(savedEvent.getPayload()).isEqualTo(jsonPayload);
        assertThat(savedEvent.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(savedEvent.getCorrelationId()).isEqualTo(correlationId);
        assertThat(savedEvent.getRetryCount()).isZero();
        assertThat(savedEvent.getPublishedAt()).isNull();
        assertThat(savedEvent.getClaimedUntil()).isNull();
        assertThat(savedEvent.getLastError()).isNull();
    }

    @Test
    @DisplayName("should persist OutboxEvent with null correlationId when MDC does not contain correlationId")
    void saveEvent_WhenMdcEmpty_ShouldSaveOutboxEventWithNullCorrelationId() throws Exception {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String eventType = "ProductCreated";
        OffsetDateTime occurredAt = OffsetDateTime.now();
        Object eventPayloadObj = new Object();
        String jsonPayload = "{\"id\":\"" + productId + "\"}";

        when(objectMapper.writeValueAsString(eventPayloadObj)).thenReturn(jsonPayload);

        // Act
        outboxService.saveEvent(eventId, productId, eventType, occurredAt, eventPayloadObj);

        // Assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent savedEvent = captor.getValue();
        assertThat(savedEvent.getCorrelationId()).isNull();
    }

    @Test
    @DisplayName("should throw IllegalStateException when event serialization fails")
    void saveEvent_WhenSerializationFails_ShouldThrowIllegalStateException() throws Exception {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String eventType = "ProductCreated";
        OffsetDateTime occurredAt = OffsetDateTime.now();
        Object eventPayloadObj = new Object();

        JacksonException jacksonException = mock(JacksonException.class);
        when(objectMapper.writeValueAsString(eventPayloadObj)).thenThrow(jacksonException);

        // Act & Assert
        assertThatThrownBy(() -> outboxService.saveEvent(eventId, productId, eventType, occurredAt, eventPayloadObj))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize outbox event: " + eventType)
                .hasCause(jacksonException);

        verify(outboxEventRepository, never()).save(any());
    }
}
