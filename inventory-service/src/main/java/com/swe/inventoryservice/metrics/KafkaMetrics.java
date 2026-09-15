package com.swe.inventoryservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class KafkaMetrics {

    private final MeterRegistry registry;

    public KafkaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordProcessing(
            String eventType,
            String outcome,
            long durationNanos
    ) {
        Timer.builder("ecommerce.kafka.processing")
                .description("Kafka event processing duration")
                .tag("event.type", eventType)
                .tag("outcome", outcome)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
