package com.swe.cartservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private CartMetrics cartMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cartMetrics = new CartMetrics(meterRegistry);
    }

    @Test
    @DisplayName("should initialize all cart counters to zero")
    void shouldInitializeCountersToZero() {
        Counter added = meterRegistry.find("ecommerce.cart.items.added").counter();
        Counter updated = meterRegistry.find("ecommerce.cart.items.updated").counter();
        Counter removed = meterRegistry.find("ecommerce.cart.items.removed").counter();
        Counter cleared = meterRegistry.find("ecommerce.carts.cleared").counter();

        assertThat(added).isNotNull();
        assertThat(added.count()).isEqualTo(0.0);

        assertThat(updated).isNotNull();
        assertThat(updated.count()).isEqualTo(0.0);

        assertThat(removed).isNotNull();
        assertThat(removed.count()).isEqualTo(0.0);

        assertThat(cleared).isNotNull();
        assertThat(cleared.count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("should increment items added counter when itemAdded is called")
    void shouldIncrementItemsAdded() {
        cartMetrics.itemAdded();
        cartMetrics.itemAdded();

        Counter added = meterRegistry.find("ecommerce.cart.items.added").counter();
        assertThat(added).isNotNull();
        assertThat(added.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should increment items updated counter when itemUpdated is called")
    void shouldIncrementItemsUpdated() {
        cartMetrics.itemUpdated();

        Counter updated = meterRegistry.find("ecommerce.cart.items.updated").counter();
        assertThat(updated).isNotNull();
        assertThat(updated.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should increment items removed counter when itemRemoved is called")
    void shouldIncrementItemsRemoved() {
        cartMetrics.itemRemoved();
        cartMetrics.itemRemoved();

        Counter removed = meterRegistry.find("ecommerce.cart.items.removed").counter();
        assertThat(removed).isNotNull();
        assertThat(removed.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should increment carts cleared counter when cartCleared is called")
    void shouldIncrementCartsCleared() {
        cartMetrics.cartCleared();

        Counter cleared = meterRegistry.find("ecommerce.carts.cleared").counter();
        assertThat(cleared).isNotNull();
        assertThat(cleared.count()).isEqualTo(1.0);
    }
}
