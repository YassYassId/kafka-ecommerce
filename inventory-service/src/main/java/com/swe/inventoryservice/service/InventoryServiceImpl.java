package com.swe.inventoryservice.service;

import com.swe.inventoryservice.entity.InventoryItem;
import com.swe.inventoryservice.event.OrderCreatedEvent;
import com.swe.inventoryservice.event.OrderCreatedItem;
import com.swe.inventoryservice.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository repository;


    @Override
    @Transactional
    public void processOrder(OrderCreatedEvent event) {
        for(OrderCreatedItem item: event.items()){
            InventoryItem inventoryItem = repository.findByProductId(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Inventory item not found for product: " +
                            item.productId()));
            if(inventoryItem.getAvailableQuantity() < item.quantity()){
                throw new IllegalStateException(
                        "Insufficient inventory for product: " + item.productId());
            }

            inventoryItem.setAvailableQuantity(inventoryItem.getAvailableQuantity() - item.quantity());
            inventoryItem.setReservedQuantity(inventoryItem.getReservedQuantity() + item.quantity());
        }
    }
}
