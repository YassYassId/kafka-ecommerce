package com.swe.inventoryservice.service;

import com.swe.inventoryservice.event.OrderCreatedEvent;

public interface InventoryService {
    void processOrder(OrderCreatedEvent event);
}
