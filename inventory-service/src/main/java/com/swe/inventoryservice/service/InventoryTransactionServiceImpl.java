package com.swe.inventoryservice.service;

import com.swe.inventoryservice.entity.InventoryItem;
import com.swe.inventoryservice.entity.ProcessedEvent;
import com.swe.inventoryservice.event.InventoryRejectedEvent;
import com.swe.inventoryservice.event.InventoryReservedEvent;
import com.swe.inventoryservice.event.OrderCreatedEvent;
import com.swe.inventoryservice.event.OrderCreatedItem;
import com.swe.inventoryservice.outbox.OutboxEvent;
import com.swe.inventoryservice.outbox.OutboxEventRepository;
import com.swe.inventoryservice.repository.InventoryItemRepository;
import com.swe.inventoryservice.repository.ProcessedEventRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryTransactionServiceImpl implements InventoryTransactionService {

    private final InventoryItemRepository repository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Retryable(retryFor = {
            OptimisticLockException.class,
            ObjectOptimisticLockingFailureException.class
    }, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void process(OrderCreatedEvent event) {
        // check if event has already been processed (Idempotency check)
        if(processedEventRepository.existsById(event.eventId())){
            return;
        }

        List<InventoryItem> inventoryItems = new ArrayList<>();

        for(OrderCreatedItem item: event.items()){
            InventoryItem inventoryItem = repository.findByProductId(item.productId())
                    .orElse(null);

            if(inventoryItem == null){
                rejectOrder(event, item.productId(), item.quantity(), 0, "PRODUCT_NOT_FOUND");
                return;
            }

            if(inventoryItem.getAvailableQuantity() < item.quantity()){
                rejectOrder(event, item.productId(), item.quantity(), inventoryItem.getAvailableQuantity(), "INSUFFICIENT_STOCK");
                return;
            }

            inventoryItems.add(inventoryItem);
        }

        for (int i = 0; i < event.items().size(); i++) {
            OrderCreatedItem requestItem = event.items().get(i);
            InventoryItem inventoryItem = inventoryItems.get(i);

            inventoryItem.setAvailableQuantity(
                    inventoryItem.getAvailableQuantity() - requestItem.quantity()
            );

            inventoryItem.setReservedQuantity(
                    inventoryItem.getReservedQuantity() + requestItem.quantity()
            );
        }

        InventoryReservedEvent reservedEvent = new InventoryReservedEvent(UUID.randomUUID(), event.orderId(),
                Instant.now(), 1);

        saveOutboxEvent(reservedEvent.eventId(), event.orderId(), "InventoryReserved", reservedEvent.version(), reservedEvent);

        markAsProcessed(event);
    }


    private void rejectOrder(OrderCreatedEvent sourceEvent, UUID productId,
                             int requestedQuantity, int availableQuantity, String reason) {

        InventoryRejectedEvent rejectedEvent = new InventoryRejectedEvent(UUID.randomUUID(), sourceEvent.orderId(), productId,
                        requestedQuantity, availableQuantity, reason, Instant.now(), 1);

        saveOutboxEvent(rejectedEvent.eventId(), sourceEvent.orderId(), "InventoryRejected", rejectedEvent.version(),
                rejectedEvent);

        markAsProcessed(sourceEvent);
    }

    private void saveOutboxEvent(UUID eventId, UUID orderId, String eventType,
            int version, Object event
    ) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(eventId)
                    .aggregateType("Order")
                    .aggregateId(orderId)
                    .eventType(eventType)
                    .eventVersion(version)
                    .payload(payload)
                    .createdAt(OffsetDateTime.now())
                    .retryCount(0)
                    .build();

            outboxEventRepository.save(outboxEvent);

        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize inventory event", e);
        }
    }

    private void markAsProcessed(OrderCreatedEvent event) {
        processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(event.eventId())
                        .processedAt(OffsetDateTime.now())
                        .build()
        );
    }
}
