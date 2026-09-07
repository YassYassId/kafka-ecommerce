package com.swe.inventoryservice.outbox;

import com.swe.inventoryservice.config.KafkaTopicConfig;
import com.swe.inventoryservice.messaging.InventoryEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventService outboxEventService;
    private final InventoryEventProducer inventoryEventProducer;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        var events = outboxEventService.claimEvents();

        for (OutboxEvent event : events) {
            try{
                String topic = resolveTopic(event.getEventType());

                inventoryEventProducer.publishInventoryEvent(topic,
                        event.getAggregateId().toString(), event.getPayload()).get();

                outboxEventService.markAsPublished(event.getId());

                log.info("Published outbox event {} of type {} to topic {}", event.getId(), event.getEventType(), topic);
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                if (errorMessage == null) {
                    errorMessage = e.getClass().getSimpleName();
                }

                try {
                    outboxEventService.recordFailure(event.getId(), errorMessage);
                } catch (Exception ex) {
                    log.error("Failed to record failure for outbox event {}: {}", event.getId(), ex.getMessage());
                }
                log.error("Failed to publish outbox event {}. Error: {}", event.getId(), errorMessage, e);
            }
        }
    }

    private String resolveTopic(String eventType){
        return switch (eventType) {
            case "InventoryReserved" -> KafkaTopicConfig.INVENTORY_RESERVED_TOPIC;
            case "InventoryRejected" -> KafkaTopicConfig.INVENTORY_REJECTED_TOPIC;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
