package com.swe.catalogservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    private final KafkaTopicConfig kafkaTopicConfig = new KafkaTopicConfig();

    @Test
    @DisplayName("should configure product created topic with 3 partitions and 1 replica")
    void productCreatedTopic_ShouldReturnConfiguredNewTopic() {
        NewTopic topic = kafkaTopicConfig.productCreatedTopic();

        assertThat(topic).isNotNull();
        assertThat(topic.name()).isEqualTo(KafkaTopicConfig.PRODUCT_CREATED_TOPIC);
        assertThat(topic.name()).isEqualTo("product.created");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("should configure product updated topic with 3 partitions and 1 replica")
    void productUpdatedTopic_ShouldReturnConfiguredNewTopic() {
        NewTopic topic = kafkaTopicConfig.productUpdatedTopic();

        assertThat(topic).isNotNull();
        assertThat(topic.name()).isEqualTo(KafkaTopicConfig.PRODUCT_UPDATED_TOPIC);
        assertThat(topic.name()).isEqualTo("product.updated");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("should configure product price changed topic with 3 partitions and 1 replica")
    void productPriceChangedTopic_ShouldReturnConfiguredNewTopic() {
        NewTopic topic = kafkaTopicConfig.productPriceChangedTopic();

        assertThat(topic).isNotNull();
        assertThat(topic.name()).isEqualTo(KafkaTopicConfig.PRODUCT_PRICE_CHANGED_TOPIC);
        assertThat(topic.name()).isEqualTo("product.price-changed");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("should configure product retired topic with 3 partitions and 1 replica")
    void productRetiredTopic_ShouldReturnConfiguredNewTopic() {
        NewTopic topic = kafkaTopicConfig.productRetiredTopic();

        assertThat(topic).isNotNull();
        assertThat(topic.name()).isEqualTo(KafkaTopicConfig.PRODUCT_RETIRED_TOPIC);
        assertThat(topic.name()).isEqualTo("product.retired");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }
}
