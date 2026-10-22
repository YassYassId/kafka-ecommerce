package com.swe.cartservice.client.order;

import java.util.Optional;

public record OrderReconciliationResult(Optional<OrderResponse> order) {
    public static OrderReconciliationResult found(OrderResponse order) {
        return new OrderReconciliationResult(Optional.of(order));
    }

    public static OrderReconciliationResult notFound() {
        return new OrderReconciliationResult(Optional.empty());
    }

    public boolean found() {
        return order.isPresent();
    }
}
