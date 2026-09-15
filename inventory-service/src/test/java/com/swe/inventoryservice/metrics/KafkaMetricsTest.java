package com.swe.inventoryservice.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private KafkaMetrics kafkaMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        kafkaMetrics = new KafkaMetrics(meterRegistry);
    }

    @Test
    @DisplayName("should record kafka processing timer with event type and success outcome")
    void shouldRecordSuccessfulKafkaProcessing() {
        long durationNanos = TimeUnit.MILLISECONDS.toNanos(120);

        kafkaMetrics.recordProcessing("OrderCreated", "success", durationNanos);

        Timer timer = meterRegistry.find("ecommerce.kafka.processing")
                .tag("event.type", "OrderCreated")
                .tag("outcome", "success")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(120.0);
    }

    @Test
    @DisplayName("should record kafka processing timer with failure outcome")
    void shouldRecordFailedKafkaProcessing() {
        long durationNanos = TimeUnit.MILLISECONDS.toNanos(45);

        kafkaMetrics.recordProcessing("OrderCreated", "failure", durationNanos);

        Timer timer = meterRegistry.find("ecommerce.kafka.processing")
                .tag("event.type", "OrderCreated")
                .tag("outcome", "failure")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(45.0);
    }
}
