package com.swe.inventoryservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private InventoryMetrics inventoryMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        inventoryMetrics = new InventoryMetrics(meterRegistry);
    }

    @Test
    @DisplayName("should initialize reservation counters to zero")
    void shouldInitializeCountersToZero() {
        Counter reserved = meterRegistry.find("ecommerce.inventory.reservations")
                .tag("outcome", "reserved")
                .counter();

        Counter rejectedStock = meterRegistry.find("ecommerce.inventory.reservations")
                .tag("outcome", "rejected")
                .tag("reason", "INSUFFICIENT_STOCK")
                .counter();

        Counter rejectedNotFound = meterRegistry.find("ecommerce.inventory.reservations")
                .tag("outcome", "rejected")
                .tag("reason", "PRODUCT_NOT_FOUND")
                .counter();

        assertThat(reserved).isNotNull();
        assertThat(reserved.count()).isEqualTo(0.0);

        assertThat(rejectedStock).isNotNull();
        assertThat(rejectedStock.count()).isEqualTo(0.0);

        assertThat(rejectedNotFound).isNotNull();
        assertThat(rejectedNotFound.count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("should increment reserved counter when reserved is called")
    void shouldIncrementReservedCounter() {
        inventoryMetrics.reserved();
        inventoryMetrics.reserved();

        Counter reserved = meterRegistry.find("ecommerce.inventory.reservations")
                .tag("outcome", "reserved")
                .counter();

        assertThat(reserved).isNotNull();
        assertThat(reserved.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should increment rejected counter with INSUFFICIENT_STOCK reason")
    void shouldIncrementRejectedInsufficientStock() {
        inventoryMetrics.rejected("INSUFFICIENT_STOCK");

        Counter rejectedStock = meterRegistry.find("ecommerce.inventory.reservations")
                .tag("outcome", "rejected")
                .tag("reason", "INSUFFICIENT_STOCK")
                .counter();

        assertThat(rejectedStock).isNotNull();
        assertThat(rejectedStock.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should increment rejected counter with PRODUCT_NOT_FOUND reason")
    void shouldIncrementRejectedProductNotFound() {
        inventoryMetrics.rejected("PRODUCT_NOT_FOUND");

        Counter rejectedNotFound = meterRegistry.find("ecommerce.inventory.reservations")
                .tag("outcome", "rejected")
                .tag("reason", "PRODUCT_NOT_FOUND")
                .counter();

        assertThat(rejectedNotFound).isNotNull();
        assertThat(rejectedNotFound.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when unsupported rejection reason is provided")
    void shouldThrowExceptionForUnsupportedReason() {
        assertThatThrownBy(() -> inventoryMetrics.rejected("UNKNOWN_REASON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported inventory rejection reason: UNKNOWN_REASON");
    }
}
