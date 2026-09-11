package com.swe.notificationsservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    private final KafkaTopicConfig kafkaTopicConfig = new KafkaTopicConfig();

    @Test
    @DisplayName("should configure inventory.reserved.notifications.dlt topic bean with 3 partitions and 1 replica")
    void shouldCreateInventoryReservedNotificationsDltTopicBean() {
        // Act
        NewTopic topic = kafkaTopicConfig.inventoryReservedNotificationsDltTopic();

        // Assert
        assertThat(topic).isNotNull();
        assertThat(topic.name()).isEqualTo(KafkaTopicConfig.INVENTORY_RESERVED_NOTIFICATIONS_DLT);
        assertThat(topic.name()).isEqualTo("inventory.reserved.notifications.dlt");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("should configure inventory.rejected.notifications.dlt topic bean with 3 partitions and 1 replica")
    void shouldCreateInventoryRejectedNotificationsDltTopicBean() {
        // Act
        NewTopic topic = kafkaTopicConfig.inventoryRejectedNotificationsDltTopic();

        // Assert
        assertThat(topic).isNotNull();
        assertThat(topic.name()).isEqualTo(KafkaTopicConfig.INVENTORY_REJECTED_NOTIFICATIONS_DLT);
        assertThat(topic.name()).isEqualTo("inventory.rejected.notifications.dlt");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }
}
