package com.swe.ordersservice.service;

import com.swe.ordersservice.dto.OrderRequest;
import com.swe.ordersservice.dto.OrderResponse;
import com.swe.ordersservice.event.InventoryRejectedEvent;
import com.swe.ordersservice.event.InventoryReservedEvent;

import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(OrderRequest orderRequest);

    OrderResponse getOrder(UUID orderId);

    void confirmOrder(InventoryReservedEvent event);

    void cancelOrder(InventoryRejectedEvent event);
}
