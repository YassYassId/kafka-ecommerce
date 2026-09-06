package com.swe.inventoryservice.service;

import com.swe.inventoryservice.event.OrderCreatedEvent;

public interface InventoryTransactionService {
    void process(OrderCreatedEvent event);
}
