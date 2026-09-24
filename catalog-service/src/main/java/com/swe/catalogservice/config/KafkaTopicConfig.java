package com.swe.catalogservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {
    public static final String PRODUCT_CREATED_TOPIC = "product.created";
    public static final String PRODUCT_UPDATED_TOPIC = "product.updated";
    public static final String PRODUCT_PRICE_CHANGED_TOPIC = "product.price-changed";
    public static final String PRODUCT_RETIRED_TOPIC = "product.retired";

    @Bean
    public NewTopic productCreatedTopic() {
        return new NewTopic(
                PRODUCT_CREATED_TOPIC,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic productUpdatedTopic() {
        return new NewTopic(
                PRODUCT_UPDATED_TOPIC,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic productPriceChangedTopic() {
        return new NewTopic(
                PRODUCT_PRICE_CHANGED_TOPIC,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic productRetiredTopic() {
        return new NewTopic(
                PRODUCT_RETIRED_TOPIC,
                3,
                (short) 1
        );
    }
}
