package com.swe.notificationsservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    public static final String INVENTORY_RESERVED_TOPIC = "inventory.reserved";
    public static final String INVENTORY_REJECTED_TOPIC = "inventory.rejected";
    public static final String INVENTORY_RESERVED_NOTIFICATIONS_DLT = "inventory.reserved.notifications.dlt";
    public static final String INVENTORY_REJECTED_NOTIFICATIONS_DLT = "inventory.rejected.notifications.dlt";

    @Bean
    public NewTopic inventoryReservedNotificationsDltTopic() {
        return new NewTopic(
                INVENTORY_RESERVED_NOTIFICATIONS_DLT,
                3,
                (short) 1
        );
    }

    @Bean
    public NewTopic inventoryRejectedNotificationsDltTopic() {
        return new NewTopic(
                INVENTORY_REJECTED_NOTIFICATIONS_DLT,
                3,
                (short) 1
        );
    }
}
