package com.swe.inventoryservice.service;

import com.swe.inventoryservice.event.ProductCreatedEvent;
import com.swe.inventoryservice.event.ProductRetiredEvent;

public interface CatalogEventService {

    void handleProductCreatedEvent(ProductCreatedEvent event);

    void handleProductRetiredEvent(ProductRetiredEvent event);
}
