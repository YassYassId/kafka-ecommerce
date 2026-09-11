package com.swe.ordersservice.config;

import com.swe.ordersservice.exception.InvalidEventException;
import com.swe.ordersservice.exception.OrderNotFoundException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    String dltTopic = switch (record.topic()) {
                        case KafkaTopicConfig.INVENTORY_RESERVED_TOPIC -> KafkaTopicConfig.INVENTORY_RESERVED_DLT;
                        case KafkaTopicConfig.INVENTORY_REJECTED_TOPIC -> KafkaTopicConfig.INVENTORY_REJECTED_DLT;
                        default -> record.topic() + ".dlt";
                    };
                    return new TopicPartition(dltTopic, record.partition());
                }
        );

        // Retry twice with 2 seconds interval
        FixedBackOff fixedBackOff = new FixedBackOff(2000L, 2L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, fixedBackOff);
        errorHandler.addNotRetryableExceptions(
                InvalidEventException.class,
                OrderNotFoundException.class
        );

        return errorHandler;
    }
}

