package com.swe.catalogservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CatalogTopicResolver topicResolver;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {

        List<OutboxEvent> events = outboxService.claimEvents();

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {

        try {
            String topic = topicResolver.resolve(event.getType());

            String key = event.getAggregateId().toString();

            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, event.getPayload());

            if (event.getCorrelationId() != null) {
                record.headers().add(new RecordHeader("X-Correlation-Id", event.getCorrelationId()
                                        .getBytes(StandardCharsets.UTF_8)));
            }

            kafkaTemplate.send(record).get();

            outboxService.markAsPublished(event.getId());

            log.info("Published Catalog outbox event eventId={} type={} productId={}", event.getId(), event.getType(),
                    event.getAggregateId());

        } catch (Exception ex) {

            log.error("Failed to publish Catalog outbox event eventId={} type={} productId={}", event.getId(),
                    event.getType(), event.getAggregateId(), ex);

            outboxService.recordFailure(event.getId(), getErrorMessage(ex));
        }
    }

    private String getErrorMessage(Exception ex) {
        String message = ex.getMessage();

        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }

        return message;
    }
}
