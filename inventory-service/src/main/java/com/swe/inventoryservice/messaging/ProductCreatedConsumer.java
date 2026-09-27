package com.swe.inventoryservice.messaging;

import com.swe.inventoryservice.event.ProductCreatedEvent;
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
public class ProductCreatedConsumer {

    private final ObjectMapper objectMapper;
    private final CatalogEventService catalogEventService;

    @KafkaListener(topics = "product.created", groupId = "inventory-service")
    public void consume(ConsumerRecord<String, String> record) {

        try {
            ProductCreatedEvent event = objectMapper.readValue(record.value(), ProductCreatedEvent.class);

            log.info("Received ProductCreated event eventId={} productId={} key={} partition={} offset={}",
                    event.eventId(), event.productId(), record.key(), record.partition(), record.offset());

            catalogEventService.handleProductCreatedEvent(event);

            log.info("Successfully processed ProductCreated event eventId={} productId={}", event.eventId(), event.productId());

        } catch (JacksonException ex) {

            log.error("Failed to deserialize ProductCreated event key={} partition={} offset={}",
                    record.key(), record.partition(), record.offset(), ex);

            throw new IllegalStateException("Failed to deserialize ProductCreated event", ex);
        }
    }
}