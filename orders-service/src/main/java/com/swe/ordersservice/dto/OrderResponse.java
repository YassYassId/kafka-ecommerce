package com.swe.ordersservice.dto;

import com.swe.ordersservice.entity.OrderStatus;

import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        OrderStatus status
) {
}