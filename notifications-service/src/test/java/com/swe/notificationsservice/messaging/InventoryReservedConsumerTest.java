package com.swe.notificationsservice.messaging;

import com.swe.notificationsservice.event.InventoryReservedEvent;
import com.swe.notificationsservice.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReservedConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private InventoryReservedConsumer consumer;

    @Nested
    @DisplayName("consume")
    class ConsumeTests {

        @Test
        @DisplayName("should deserialize valid JSON payload and delegate to notification service")
        void shouldDeserializeAndHandleInventoryReservedSuccessfully() throws Exception {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            InventoryReservedEvent event = new InventoryReservedEvent(
                    eventId,
                    orderId,
                    Instant.now(),
                    1
            );

            String jsonPayload = objectMapper.writeValueAsString(event);

            consumer.consume(orderId.toString(), jsonPayload);

            ArgumentCaptor<InventoryReservedEvent> captor = ArgumentCaptor.forClass(InventoryReservedEvent.class);
            verify(notificationService).handleInventoryReservedEvent(captor.capture());

            InventoryReservedEvent capturedEvent = captor.getValue();
            assertThat(capturedEvent.eventId()).isEqualTo(eventId);
            assertThat(capturedEvent.orderId()).isEqualTo(orderId);
            assertThat(capturedEvent.version()).isEqualTo(1);
            assertThat(capturedEvent.occurredAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw IllegalStateException when payload is malformed JSON")
        void shouldThrowIllegalStateExceptionWhenJsonIsMalformed() {
            String malformedPayload = "{ invalid json content }";

            assertThatThrownBy(() -> consumer.consume("test-key", malformedPayload))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to process InventoryReserved event");

            verifyNoInteractions(notificationService);
        }
    }
}
