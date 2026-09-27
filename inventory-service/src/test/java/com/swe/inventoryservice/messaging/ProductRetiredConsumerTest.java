package com.swe.inventoryservice.messaging;

import com.swe.inventoryservice.event.ProductRetiredEvent;
import com.swe.inventoryservice.service.CatalogEventService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProductRetiredConsumerTest {

    @Mock
    private CatalogEventService catalogEventService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProductRetiredConsumer consumer;

    @Nested
    @DisplayName("consume")
    class ConsumeTests {

        @Test
        @DisplayName("should deserialize valid JSON payload and delegate to CatalogEventService")
        void shouldDeserializeAndProcessValidProductRetiredEvent() throws Exception {
            UUID eventId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            OffsetDateTime occurredAt = OffsetDateTime.now();

            ProductRetiredEvent event = new ProductRetiredEvent(
                    eventId,
                    productId,
                    "SKU-456",
                    occurredAt,
                    1
            );

            String jsonPayload = objectMapper.writeValueAsString(event);
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "product.retired", 0, 100L, productId.toString(), jsonPayload
            );

            consumer.consume(record);

            ArgumentCaptor<ProductRetiredEvent> captor = ArgumentCaptor.forClass(ProductRetiredEvent.class);
            verify(catalogEventService).handleProductRetiredEvent(captor.capture());

            ProductRetiredEvent capturedEvent = captor.getValue();
            assertThat(capturedEvent.eventId()).isEqualTo(eventId);
            assertThat(capturedEvent.productId()).isEqualTo(productId);
            assertThat(capturedEvent.sku()).isEqualTo("SKU-456");
            assertThat(capturedEvent.version()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw IllegalStateException when payload is malformed JSON")
        void shouldThrowIllegalStateExceptionWhenJsonIsMalformed() {
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "product.retired", 0, 100L, "test-key", "{ invalid json content }"
            );

            assertThatThrownBy(() -> consumer.consume(record))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to deserialize ProductRetired event");

            verifyNoInteractions(catalogEventService);
        }
    }
}
