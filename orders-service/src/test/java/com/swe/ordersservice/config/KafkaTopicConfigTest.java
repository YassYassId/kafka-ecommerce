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
}
