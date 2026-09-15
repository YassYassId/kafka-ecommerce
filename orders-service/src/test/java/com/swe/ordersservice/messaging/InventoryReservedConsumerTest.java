package com.swe.ordersservice.messaging;

import com.swe.ordersservice.event.InventoryReservedEvent;
import com.swe.ordersservice.exception.InvalidEventException;
import com.swe.ordersservice.metrics.KafkaMetrics;
import com.swe.ordersservice.service.OrderService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReservedConsumerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private KafkaMetrics kafkaMetrics;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private InventoryReservedConsumer consumer;

    @Nested
    @DisplayName("consume")
    class ConsumeTests {

        @Test
        @DisplayName("should deserialize valid JSON payload and delegate to order service to confirm order")
        void shouldDeserializeAndConfirmOrderSuccessfully() throws Exception {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            InventoryReservedEvent event = new InventoryReservedEvent(
                    eventId,
                    orderId,
                    Instant.now(),
                    1
            );

            String jsonPayload = objectMapper.writeValueAsString(event);

            String correlationId = UUID.randomUUID().toString();
            consumer.consume(orderId.toString(), jsonPayload, correlationId);

            ArgumentCaptor<InventoryReservedEvent> captor = ArgumentCaptor.forClass(InventoryReservedEvent.class);
            verify(orderService).confirmOrder(captor.capture());

            InventoryReservedEvent capturedEvent = captor.getValue();
            assertThat(capturedEvent.eventId()).isEqualTo(eventId);
            assertThat(capturedEvent.orderId()).isEqualTo(orderId);
            assertThat(capturedEvent.version()).isEqualTo(1);
            assertThat(capturedEvent.occurredAt()).isNotNull();

            verify(kafkaMetrics).recordProcessing(eq("InventoryReserved"), eq("success"), anyLong());
        }

        @Test
        @DisplayName("should throw InvalidEventException when payload is malformed JSON")
        void shouldThrowInvalidEventExceptionWhenJsonIsMalformed() {
            String malformedPayload = "{ invalid json content }";

            assertThatThrownBy(() -> consumer.consume("test-key", malformedPayload, null))
                    .isInstanceOf(InvalidEventException.class)
                    .hasMessageContaining("Failed to deserialize InventoryReserved event");

            verifyNoInteractions(orderService);
            verify(kafkaMetrics).recordProcessing(eq("InventoryReserved"), eq("failure"), anyLong());
        }
    }
}

