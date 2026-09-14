package com.swe.ordersservice.outbox;


import com.swe.ordersservice.messaging.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OrderEventProducer orderEventProducer;
    private final OutboxEventService outboxEventService;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {

        var events = outboxEventService.claimEvents();

        for (OutboxEvent event : events) {
            try {
                MDC.put("correlationId", event.getCorrelationId());
                MDC.put("outboxEventId", event.getId().toString());
                MDC.put("orderId", event.getAggregateId().toString());
                MDC.put("eventType", event.getEventType());

                orderEventProducer.publishOrderCreatedEvent(
                        event.getAggregateId().toString(), event.getPayload(), event.getCorrelationId())
                        .get();

                outboxEventService.markAsPublished(event.getId());

                log.info("Published outbox event to Kafka");
            } catch (Exception e) {
                String errorMessage = e.getMessage();

                if (errorMessage == null) {
                    errorMessage = e.getClass().getSimpleName();
                }

                try {
                    outboxEventService.recordFailure(
                            event.getId(),
                            errorMessage
                    );
                } catch (Exception updateException) {
                    log.error("Failed to publish outbox event to Kafka", e);
                }

                log.error(
                        "Failed to publish outbox event. Error: {}",
                        errorMessage,
                        e
                );
            } finally {
                MDC.remove("correlationId");
                MDC.remove("outboxEventId");
                MDC.remove("orderId");
                MDC.remove("eventType");
            }
        }
    }
}
