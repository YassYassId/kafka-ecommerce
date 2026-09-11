package com.swe.ordersservice.messaging;

import com.swe.ordersservice.event.InventoryRejectedEvent;
import com.swe.ordersservice.exception.InvalidEventException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryRejectedConsumerTest {

    @Mock
    private OrderService orderService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private InventoryRejectedConsumer consumer;

    @Nested
    @DisplayName("consume")
    class ConsumeTests {

        @Test
        @DisplayName("should deserialize valid JSON payload and delegate to order service to cancel order")
        void shouldDeserializeAndCancelOrderSuccessfully() throws Exception {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            InventoryRejectedEvent event = new InventoryRejectedEvent(
                    eventId,
                    orderId,
                    productId,
                    5,
                    2,
                    "INSUFFICIENT_STOCK",
                    Instant.now(),
                    1
            );

            String jsonPayload = objectMapper.writeValueAsString(event);

            consumer.consume(orderId.toString(), jsonPayload);

            ArgumentCaptor<InventoryRejectedEvent> captor = ArgumentCaptor.forClass(InventoryRejectedEvent.class);
            verify(orderService).cancelOrder(captor.capture());

            InventoryRejectedEvent capturedEvent = captor.getValue();
            assertThat(capturedEvent.eventId()).isEqualTo(eventId);
            assertThat(capturedEvent.orderId()).isEqualTo(orderId);
            assertThat(capturedEvent.productId()).isEqualTo(productId);
            assertThat(capturedEvent.requestedQuantity()).isEqualTo(5);
            assertThat(capturedEvent.availableQuantity()).isEqualTo(2);
            assertThat(capturedEvent.reason()).isEqualTo("INSUFFICIENT_STOCK");
            assertThat(capturedEvent.version()).isEqualTo(1);
            assertThat(capturedEvent.occurredAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw InvalidEventException when payload is malformed JSON")
        void shouldThrowInvalidEventExceptionWhenJsonIsMalformed() {
            String malformedPayload = "{ invalid json content }";

            assertThatThrownBy(() -> consumer.consume("test-key", malformedPayload))
                    .isInstanceOf(InvalidEventException.class)
                    .hasMessageContaining("Failed to deserialize InventoryRejected event");

            verifyNoInteractions(orderService);
        }
    }
}

