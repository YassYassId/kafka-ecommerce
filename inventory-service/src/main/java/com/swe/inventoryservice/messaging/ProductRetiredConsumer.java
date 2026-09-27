package com.swe.inventoryservice.messaging;

import com.swe.inventoryservice.event.ProductRetiredEvent;
import com.swe.inventoryservice.service.CatalogEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRetiredConsumer {

    private final ObjectMapper objectMapper;
    private final CatalogEventService catalogEventService;

    @KafkaListener(topics = "product.retired", groupId = "inventory-service")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            ProductRetiredEvent event = objectMapper.readValue(record.value(), ProductRetiredEvent.class);

            log.info("Received ProductRetired event eventId={} productId={}", event.eventId(), event.productId());

            catalogEventService.handleProductRetiredEvent(event);

        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize ProductRetired event", ex);
        }
    }
}
