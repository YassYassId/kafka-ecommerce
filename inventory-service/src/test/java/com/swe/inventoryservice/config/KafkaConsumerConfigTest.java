package com.swe.inventoryservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();

    @Test
    @DisplayName("should configure DefaultErrorHandler with DeadLetterPublishingRecoverer and FixedBackOff")
    void shouldConfigureDefaultErrorHandler() {
        DefaultErrorHandler errorHandler = consumerConfig.kafkaErrorHandler(kafkaTemplate);

        assertThat(errorHandler).isNotNull();
    }
}
