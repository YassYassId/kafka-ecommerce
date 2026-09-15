package com.swe.ordersservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter ordersCreated;
    private final Counter ordersConfirmed;
    private final Counter ordersCancelled;

    public OrderMetrics(MeterRegistry registry) {
        this.ordersCreated = Counter.builder("ecommerce.orders.created")
                .description("Number of successfully created orders")
                .register(registry);

        this.ordersConfirmed = Counter.builder("ecommerce.orders.confirmed")
                .description("Number of successfully confirmed orders")
                .register(registry);

        this.ordersCancelled = Counter.builder("ecommerce.orders.cancelled")
                .description("Number of successfully cancelled orders")
                .register(registry);
    }

    public void orderCreated() {
        ordersCreated.increment();
    }

    public void orderConfirmed() {
        ordersConfirmed.increment();
    }

    public void orderCancelled() {
        ordersCancelled.increment();
    }
}
