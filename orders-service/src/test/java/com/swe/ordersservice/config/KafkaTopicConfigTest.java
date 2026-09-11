package com.swe.ordersservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    private final KafkaTopicConfig kafkaTopicConfig = new KafkaTopicConfig();

    @Test
    @DisplayName("should configure order created topic with expected name, partitions, and replication factor")
    void orderCreatedTopic_ShouldReturnConfiguredNewTopic() {
        // Act
        NewTopic topic = kafkaTopicConfig.orderCreatedTopic();

        // Assert
        assertThat(topic).isNotNull();
        assertThat(topic.name()).isEqualTo(KafkaTopicConfig.ORDER_CREATED_TOPIC);
        assertThat(topic.name()).isEqualTo("order.created");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("should configure inventory.reserved.dlt topic bean with 3 partitions and 1 replica")
    void shouldCreateInventoryReservedDltTopicBean() {
        // Act
        NewTopic topic = kafkaTopicConfig.inventoryReservedDltTopic();

        // Assert
        assertThat(topic).isNotNull();
        assertThat(topic.name()).isEqualTo(KafkaTopicConfig.INVENTORY_RESERVED_DLT);
        assertThat(topic.name()).isEqualTo("inventory.reserved.dlt");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("should configure inventory.rejected.dlt topic bean with 3 partitions and 1 replica")
    void shouldCreateInventoryRejectedDltTopicBean() {
        // Act
        NewTopic topic = kafkaTopicConfig.inventoryRejectedDltTopic();

        // Assert
        assertThat(topic).isNotNull();
        assertThat(topic.name()).isEqualTo(KafkaTopicConfig.INVENTORY_REJECTED_DLT);
        assertThat(topic.name()).isEqualTo("inventory.rejected.dlt");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }
}

