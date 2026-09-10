package com.swe.notificationsservice.service;

import com.swe.notificationsservice.event.InventoryRejectedEvent;
import com.swe.notificationsservice.event.InventoryReservedEvent;

public interface NotificationService {

    void handleInventoryReservedEvent(InventoryReservedEvent event);

    void handleInventoryRejectedEvent(InventoryRejectedEvent event);
}
