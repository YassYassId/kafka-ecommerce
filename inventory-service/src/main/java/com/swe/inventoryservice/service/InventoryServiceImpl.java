package com.swe.inventoryservice.service;

import com.swe.inventoryservice.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryTransactionService inventoryTransactionService;

    @Override
    public void processOrder(OrderCreatedEvent event) {
        inventoryTransactionService.process(event);
    }
}
