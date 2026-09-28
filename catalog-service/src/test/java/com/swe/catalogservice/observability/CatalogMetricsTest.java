package com.swe.catalogservice.observability;
 
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private CatalogMetrics catalogMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        catalogMetrics = new CatalogMetrics(meterRegistry);
    }

    @Test
    @DisplayName("should initialize all catalog counters to zero")
    void shouldInitializeCountersToZero() {
        Counter created = meterRegistry.find("ecommerce.catalog.products.created").counter();
        Counter updated = meterRegistry.find("ecommerce.catalog.products.updated").counter();
        Counter priceChanged = meterRegistry.find("ecommerce.catalog.prices.changed").counter();
        Counter retired = meterRegistry.find("ecommerce.catalog.products.retired").counter();

        assertThat(created).isNotNull();
        assertThat(created.count()).isEqualTo(0.0);

        assertThat(updated).isNotNull();
        assertThat(updated.count()).isEqualTo(0.0);

        assertThat(priceChanged).isNotNull();
        assertThat(priceChanged.count()).isEqualTo(0.0);

        assertThat(retired).isNotNull();
        assertThat(retired.count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("should increment products created counter when productCreated is called")
    void shouldIncrementProductCreated() {
        catalogMetrics.productCreated();
        catalogMetrics.productCreated();

        Counter created = meterRegistry.find("ecommerce.catalog.products.created").counter();
        assertThat(created).isNotNull();
        assertThat(created.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should increment products updated counter when productUpdated is called")
    void shouldIncrementProductUpdated() {
        catalogMetrics.productUpdated();

        Counter updated = meterRegistry.find("ecommerce.catalog.products.updated").counter();
        assertThat(updated).isNotNull();
        assertThat(updated.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should increment prices changed counter when priceChanged is called")
    void shouldIncrementPriceChanged() {
        catalogMetrics.priceChanged();
        catalogMetrics.priceChanged();

        Counter priceChanged = meterRegistry.find("ecommerce.catalog.prices.changed").counter();
        assertThat(priceChanged).isNotNull();
        assertThat(priceChanged.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should increment products retired counter when productRetired is called")
    void shouldIncrementProductRetired() {
        catalogMetrics.productRetired();

        Counter retired = meterRegistry.find("ecommerce.catalog.products.retired").counter();
        assertThat(retired).isNotNull();
        assertThat(retired.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should increment events published counter tagged with event type")
    void shouldIncrementEventPublishedWithTag() {
        catalogMetrics.eventPublished("ProductCreated");
        catalogMetrics.eventPublished("ProductCreated");
        catalogMetrics.eventPublished("PriceChanged");

        Counter createdEvents = meterRegistry.find("ecommerce.catalog.events.published")
                .tag("event.type", "ProductCreated")
                .counter();
        Counter priceChangedEvents = meterRegistry.find("ecommerce.catalog.events.published")
                .tag("event.type", "PriceChanged")
                .counter();

        assertThat(createdEvents).isNotNull();
        assertThat(createdEvents.count()).isEqualTo(2.0);

        assertThat(priceChangedEvents).isNotNull();
        assertThat(priceChangedEvents.count()).isEqualTo(1.0);
    }
}
