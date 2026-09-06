package com.swe.inventoryservice.messaging;

import com.swe.inventoryservice.event.OrderCreatedEvent;
import com.swe.inventoryservice.event.OrderCreatedItem;
import com.swe.inventoryservice.exception.InvalidEventException;
import com.swe.inventoryservice.service.InventoryService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCreatedConsumerTest {

    @Mock
    private InventoryService inventoryService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderCreatedConsumer consumer;

    @Nested
    @DisplayName("consume")
    class ConsumeTests {

        @Test
        @DisplayName("should deserialize valid JSON payload and delegate to inventory service")
        void shouldDeserializeAndProcessValidEvent() throws Exception {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId,
                    orderId,
                    customerId,
                    List.of(new OrderCreatedItem(productId, 2)),
                    Instant.now(),
                    1
            );

            String jsonPayload = objectMapper.writeValueAsString(event);

            consumer.consume(orderId.toString(), jsonPayload);

            ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(inventoryService).processOrder(captor.capture());

            OrderCreatedEvent capturedEvent = captor.getValue();
            assertThat(capturedEvent.eventId()).isEqualTo(eventId);
            assertThat(capturedEvent.orderId()).isEqualTo(orderId);
            assertThat(capturedEvent.customerId()).isEqualTo(customerId);
            assertThat(capturedEvent.items()).hasSize(1);
            assertThat(capturedEvent.items().getFirst().productId()).isEqualTo(productId);
            assertThat(capturedEvent.items().getFirst().quantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw InvalidEventException when payload is malformed JSON")
        void shouldThrowInvalidEventExceptionWhenJsonIsMalformed() {
            String malformedPayload = "{ invalid json content }";

            assertThatThrownBy(() -> consumer.consume("test-key", malformedPayload))
                    .isInstanceOf(InvalidEventException.class)
                    .hasMessageContaining("Failed to deserialize OrderCreated event");

            verifyNoInteractions(inventoryService);
        }
    }
}
