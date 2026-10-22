package com.swe.cartservice.client.order;

public interface OrderClient {
    OrderResponse createOrder(String idempotencyKey, CreateOrderRequest request);

    OrderReconciliationResult reconcileCheckout(String idempotencyKey, CreateOrderRequest request);
}
