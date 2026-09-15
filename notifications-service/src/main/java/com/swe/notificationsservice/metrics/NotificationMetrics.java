package com.swe.notificationsservice.metrics;

import com.swe.notificationsservice.entity.NotificationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import static com.swe.notificationsservice.entity.NotificationType.ORDER_CANCELLED;
import static com.swe.notificationsservice.entity.NotificationType.ORDER_CONFIRMED;

@Component
public class NotificationMetrics {

    private final Counter orderConfirmedNotifications;
    private final Counter orderCancelledNotifications;

    public NotificationMetrics(MeterRegistry registry) {
        this.orderConfirmedNotifications =
                Counter.builder("ecommerce.notifications.created")
                        .description("Number of notifications created")
                        .tag(
                                "type",
                                NotificationType.ORDER_CONFIRMED.name()
                        )
                        .register(registry);

        this.orderCancelledNotifications =
                Counter.builder("ecommerce.notifications.created")
                        .description("Number of notifications created")
                        .tag(
                                "type",
                                NotificationType.ORDER_CANCELLED.name()
                        )
                        .register(registry);
    }

    public void created(NotificationType type) {
        switch (type) {
            case ORDER_CONFIRMED ->
                    orderConfirmedNotifications.increment();

            case ORDER_CANCELLED ->
                    orderCancelledNotifications.increment();
        }
    }
}
