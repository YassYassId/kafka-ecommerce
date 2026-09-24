package com.swe.catalogservice.outbox;

import com.swe.catalogservice.config.KafkaTopicConfig;
import org.springframework.stereotype.Component;

@Component
public class CatalogTopicResolver {

    public String resolve(String eventType) {
        return switch (eventType) {
            case "ProductCreated" -> KafkaTopicConfig.PRODUCT_CREATED_TOPIC;
            case "ProductUpdated" -> KafkaTopicConfig.PRODUCT_UPDATED_TOPIC;
            case "PriceChanged" -> KafkaTopicConfig.PRODUCT_PRICE_CHANGED_TOPIC;
            case "ProductRetired" -> KafkaTopicConfig.PRODUCT_RETIRED_TOPIC;
            default -> throw new IllegalArgumentException(
                    "Unsupported Catalog event type: " + eventType
            );
        };
    }
}
