package com.swe.ordersservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Since in the docker-compose.yml file, we have defined
 * "KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false""
 * we need to create the topic manually in the code.
 * Otherwise, the topic will not be created automatically when the application starts.
 */
@Configuration
public class KafkaTopicConfig {
    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String INVENTORY_RESERVED_TOPIC = "inventory.reserved";
    public static final String INVENTORY_REJECTED_TOPIC = "inventory.rejected";
    public static final String INVENTORY_RESERVED_DLT = "inventory.reserved.dlt";
    public static final String INVENTORY_REJECTED_DLT = "inventory.rejected.dlt";

    @Bean
    public NewTopic orderCreatedTopic() {
        return new NewTopic(
                ORDER_CREATED_TOPIC,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic inventoryReservedDltTopic() {
        return new NewTopic(
                INVENTORY_RESERVED_DLT,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic inventoryRejectedDltTopic() {
        return new NewTopic(
                INVENTORY_REJECTED_DLT,
                3,
                (short) 1
        );
    }
}

