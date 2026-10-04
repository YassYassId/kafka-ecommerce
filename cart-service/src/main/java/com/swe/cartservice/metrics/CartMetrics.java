package com.swe.cartservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CartMetrics {

    private final Counter itemsAdded;
    private final Counter itemsUpdated;
    private final Counter itemsRemoved;
    private final Counter cartsCleared;

    public CartMetrics(MeterRegistry registry) {
        this.itemsAdded = Counter.builder("ecommerce.cart.items.added")
                .description("Number of cart items added")
                .register(registry);

        this.itemsUpdated = Counter.builder("ecommerce.cart.items.updated")
                .description("Number of cart item quantities updated")
                .register(registry);

        this.itemsRemoved = Counter.builder("ecommerce.cart.items.removed")
                .description("Number of cart items removed")
                .register(registry);

        this.cartsCleared = Counter.builder("ecommerce.carts.cleared")
                .description("Number of carts cleared")
                .register(registry);
    }

    public void itemAdded() {
        itemsAdded.increment();
    }

    public void itemUpdated() {
        itemsUpdated.increment();
    }

    public void itemRemoved() {
        itemsRemoved.increment();
    }

    public void cartCleared() {
        cartsCleared.increment();
    }
}
