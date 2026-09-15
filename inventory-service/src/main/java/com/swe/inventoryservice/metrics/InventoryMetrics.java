package com.swe.inventoryservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InventoryMetrics {

    private final Counter reservationsReserved;
    private final Counter reservationsRejectedInsufficientStock;
    private final Counter reservationsRejectedProductNotFound;

    public InventoryMetrics(MeterRegistry registry) {

        this.reservationsReserved =
                Counter.builder("ecommerce.inventory.reservations")
                        .description("Inventory reservation decisions")
                        .tag("outcome", "reserved")
                        .register(registry);

        this.reservationsRejectedInsufficientStock =
                Counter.builder("ecommerce.inventory.reservations")
                        .description("Inventory reservation decisions")
                        .tag("outcome", "rejected")
                        .tag("reason", "INSUFFICIENT_STOCK")
                        .register(registry);

        this.reservationsRejectedProductNotFound =
                Counter.builder("ecommerce.inventory.reservations")
                        .description("Inventory reservation decisions")
                        .tag("outcome", "rejected")
                        .tag("reason", "PRODUCT_NOT_FOUND")
                        .register(registry);
    }

    public void reserved() {
        reservationsReserved.increment();
    }

    public void rejected(String reason) {
        switch (reason) {
            case "INSUFFICIENT_STOCK" ->
                    reservationsRejectedInsufficientStock.increment();

            case "PRODUCT_NOT_FOUND" ->
                    reservationsRejectedProductNotFound.increment();

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported inventory rejection reason: " + reason
                    );
        }
    }
}
