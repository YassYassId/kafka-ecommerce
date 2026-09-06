package com.swe.inventoryservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_CREATED_DLT = "order.created.dlt";

    @Bean
    public NewTopic orderCreatedDltTopic() {
        return new NewTopic(
                ORDER_CREATED_DLT,
                3,
                (short) 1
        );
    }
}
