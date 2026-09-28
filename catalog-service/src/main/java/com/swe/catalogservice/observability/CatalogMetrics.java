package com.swe.catalogservice.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CatalogMetrics {

    private final Counter productsCreated;
    private final Counter productsUpdated;
    private final Counter pricesChanged;
    private final Counter productsRetired;
    private final MeterRegistry meterRegistry;

    public CatalogMetrics(MeterRegistry registry) {
        this.meterRegistry = registry;
        this.productsCreated = Counter.builder("ecommerce.catalog.products.created")
                .description("Number of products created")
                .register(registry);

        this.productsUpdated = Counter.builder("ecommerce.catalog.products.updated")
                .description("Number of products updated")
                .register(registry);

        this.pricesChanged = Counter.builder("ecommerce.catalog.prices.changed")
                .description("Number of product price changes")
                .register(registry);

        this.productsRetired = Counter.builder("ecommerce.catalog.products.retired")
                .description("Number of products retired")
                .register(registry);
    }

    public void productCreated() {
        productsCreated.increment();
    }

    public void productUpdated() {
        productsUpdated.increment();
    }

    public void priceChanged() {
        pricesChanged.increment();
    }

    public void productRetired() {
        productsRetired.increment();
    }

    public void eventPublished(String eventType) {
        Counter.builder("ecommerce.catalog.events.published")
                .description("Number of Catalog events successfully published to Kafka")
                .tag("event.type", eventType)
                .register(meterRegistry)
                .increment();
    }
}