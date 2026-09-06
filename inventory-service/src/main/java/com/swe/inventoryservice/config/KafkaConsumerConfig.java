package com.swe.inventoryservice.config;

import com.swe.inventoryservice.exception.InsufficientInventoryException;
import com.swe.inventoryservice.exception.InvalidEventException;
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
                (record, exception) ->
                        new TopicPartition(
                                KafkaTopicConfig.ORDER_CREATED_DLT,
                                record.partition())
        );

        // Retry twice with 2 seconds interval
        FixedBackOff fixedBackOff = new FixedBackOff(2000L, 2L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, fixedBackOff);
        errorHandler.addNotRetryableExceptions(
                IllegalStateException.class,
                InsufficientInventoryException.class,
                InvalidEventException.class);

        return errorHandler;
    }
}
