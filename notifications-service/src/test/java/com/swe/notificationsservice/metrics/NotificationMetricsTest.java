package com.swe.notificationsservice.metrics;

import com.swe.notificationsservice.entity.NotificationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private NotificationMetrics notificationMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        notificationMetrics = new NotificationMetrics(meterRegistry);
    }

    @Test
    @DisplayName("should initialize notification counters to zero")
    void shouldInitializeCountersToZero() {
        Counter confirmed = meterRegistry.find("ecommerce.notifications.created")
                .tag("type", NotificationType.ORDER_CONFIRMED.name())
                .counter();

        Counter cancelled = meterRegistry.find("ecommerce.notifications.created")
                .tag("type", NotificationType.ORDER_CANCELLED.name())
                .counter();

        assertThat(confirmed).isNotNull();
        assertThat(confirmed.count()).isEqualTo(0.0);

        assertThat(cancelled).isNotNull();
        assertThat(cancelled.count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("should increment order confirmed notifications counter")
    void shouldIncrementOrderConfirmedNotifications() {
        notificationMetrics.created(NotificationType.ORDER_CONFIRMED);
        notificationMetrics.created(NotificationType.ORDER_CONFIRMED);

        Counter confirmed = meterRegistry.find("ecommerce.notifications.created")
                .tag("type", NotificationType.ORDER_CONFIRMED.name())
                .counter();

        assertThat(confirmed).isNotNull();
        assertThat(confirmed.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should increment order cancelled notifications counter")
    void shouldIncrementOrderCancelledNotifications() {
        notificationMetrics.created(NotificationType.ORDER_CANCELLED);

        Counter cancelled = meterRegistry.find("ecommerce.notifications.created")
                .tag("type", NotificationType.ORDER_CANCELLED.name())
                .counter();

        assertThat(cancelled).isNotNull();
        assertThat(cancelled.count()).isEqualTo(1.0);
    }
}
