package com.swe.inventoryservice.service;

import com.swe.inventoryservice.entity.InventoryItem;
import com.swe.inventoryservice.entity.ProcessedEvent;
import com.swe.inventoryservice.event.ProductCreatedEvent;
import com.swe.inventoryservice.event.ProductRetiredEvent;
import com.swe.inventoryservice.repository.InventoryItemRepository;
import com.swe.inventoryservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogEventServiceImpl implements CatalogEventService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Override
    @Transactional
    public void handleProductCreatedEvent(ProductCreatedEvent event) {
        // Same Kafka event delivered again
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("ProductCreated already processed eventId={} productId={}", event.eventId(), event.productId());
            return;
        }

        // Protect the product identity independently of eventId
        if (!inventoryItemRepository.existsByProductId(event.productId())) {

            InventoryItem inventoryItem = InventoryItem.builder()
                    .productId(event.productId())
                    .availableQuantity(0)
                    .reservedQuantity(0)
                    .productActive(true)
                    .build();

            inventoryItemRepository.save(inventoryItem);

            log.info("Initialized inventory for Catalog product productId={}", event.productId());
        } else {
            log.info("Inventory already exists for Catalog product productId={}", event.productId());
        }

        processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(event.eventId())
                        .processedAt(OffsetDateTime.now())
                        .build());
    }

    @Override
    @Transactional
    public void handleProductRetiredEvent(ProductRetiredEvent event) {

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("ProductRetired event already processed eventId={} productId={}", event.eventId(), event.productId());
            return;
        }

        InventoryItem inventoryItem = inventoryItemRepository.findByProductId(event.productId())
                        .orElse(null);

        if (inventoryItem != null) {
            inventoryItem.setProductActive(false);

            log.info("Marked inventory product as inactive productId={}", event.productId());
        } else {
            log.warn("Received ProductRetired for unknown product productId={}", event.productId());
        }

        processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(event.eventId())
                        .processedAt(OffsetDateTime.now())
                        .build());
    }
}
