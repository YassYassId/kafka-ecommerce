package com.swe.inventoryservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_CREATED_DLT = "order.created.dlt";
    public static final String INVENTORY_RESERVED_TOPIC = "inventory.reserved";
    public static final String INVENTORY_REJECTED_TOPIC = "inventory.rejected";

    @Bean
    public NewTopic orderCreatedDltTopic() {
        return new NewTopic(
                ORDER_CREATED_DLT,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic inventoryReservedTopic() {
        return new NewTopic(
                INVENTORY_RESERVED_TOPIC,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic inventoryRejectedTopic() {
        return new NewTopic(
                INVENTORY_REJECTED_TOPIC,
                3,
                (short) 1
        );
    }
}
