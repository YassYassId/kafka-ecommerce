package com.swe.inventoryservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    private final KafkaTopicConfig topicConfig = new KafkaTopicConfig();

    @Test
    @DisplayName("should configure order.created.dlt topic bean with 3 partitions and 1 replica")
    void shouldCreateOrderCreatedDltTopicBean() {
        NewTopic topic = topicConfig.orderCreatedDltTopic();

        assertThat(topic).isNotNull();
        assertThat(topic.name()).isEqualTo(KafkaTopicConfig.ORDER_CREATED_DLT);
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }
}
