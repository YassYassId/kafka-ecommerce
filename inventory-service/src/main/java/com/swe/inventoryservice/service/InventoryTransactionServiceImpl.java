package com.swe.inventoryservice.service;

import com.swe.inventoryservice.entity.InventoryItem;
import com.swe.inventoryservice.entity.ProcessedEvent;
import com.swe.inventoryservice.event.OrderCreatedEvent;
import com.swe.inventoryservice.event.OrderCreatedItem;
import com.swe.inventoryservice.exception.InsufficientInventoryException;
import com.swe.inventoryservice.repository.InventoryItemRepository;
import com.swe.inventoryservice.repository.ProcessedEventRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class InventoryTransactionServiceImpl implements InventoryTransactionService {

    private final InventoryItemRepository repository;
    private final ProcessedEventRepository processedEventRepository;

    @Override
    @Retryable(retryFor = {
            OptimisticLockException.class,
            ObjectOptimisticLockingFailureException.class
    }, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void process(OrderCreatedEvent event) {
        if(processedEventRepository.existsById(event.eventId())){
            return;
        }

        for(OrderCreatedItem item: event.items()){
            InventoryItem inventoryItem = repository.findByProductId(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Inventory item not found for product: " +
                                    item.productId()));

            if(inventoryItem.getAvailableQuantity() < item.quantity()){
                throw new InsufficientInventoryException(item.productId());
            }

            inventoryItem.setAvailableQuantity(
                    inventoryItem.getAvailableQuantity() - item.quantity());
            inventoryItem.setReservedQuantity(
                    inventoryItem.getReservedQuantity() + item.quantity());
        }

        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(event.eventId())
                .processedAt(OffsetDateTime.now())
                .build());
    }
}
