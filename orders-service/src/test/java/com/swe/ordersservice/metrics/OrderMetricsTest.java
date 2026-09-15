package com.swe.ordersservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private OrderMetrics orderMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orderMetrics = new OrderMetrics(meterRegistry);
    }

    @Test
    @DisplayName("should initialize all order counters to zero")
    void shouldInitializeCountersToZero() {
        Counter created = meterRegistry.find("ecommerce.orders.created").counter();
        Counter confirmed = meterRegistry.find("ecommerce.orders.confirmed").counter();
        Counter cancelled = meterRegistry.find("ecommerce.orders.cancelled").counter();

        assertThat(created).isNotNull();
        assertThat(created.count()).isEqualTo(0.0);

        assertThat(confirmed).isNotNull();
        assertThat(confirmed.count()).isEqualTo(0.0);

        assertThat(cancelled).isNotNull();
        assertThat(cancelled.count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("should increment orders created counter when orderCreated is called")
    void shouldIncrementOrderCreated() {
        orderMetrics.orderCreated();
        orderMetrics.orderCreated();

        Counter created = meterRegistry.find("ecommerce.orders.created").counter();
        assertThat(created).isNotNull();
        assertThat(created.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should increment orders confirmed counter when orderConfirmed is called")
    void shouldIncrementOrderConfirmed() {
        orderMetrics.orderConfirmed();

        Counter confirmed = meterRegistry.find("ecommerce.orders.confirmed").counter();
        assertThat(confirmed).isNotNull();
        assertThat(confirmed.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should increment orders cancelled counter when orderCancelled is called")
    void shouldIncrementOrderCancelled() {
        orderMetrics.orderCancelled();

        Counter cancelled = meterRegistry.find("ecommerce.orders.cancelled").counter();
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.count()).isEqualTo(1.0);
    }
}
