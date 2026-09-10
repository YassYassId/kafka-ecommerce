package com.swe.notificationsservice.service;

import com.swe.notificationsservice.entity.Notification;
import com.swe.notificationsservice.entity.NotificationStatus;
import com.swe.notificationsservice.entity.NotificationType;
import com.swe.notificationsservice.event.InventoryRejectedEvent;
import com.swe.notificationsservice.event.InventoryReservedEvent;
import com.swe.notificationsservice.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Nested
    @DisplayName("handleInventoryReservedEvent")
    class HandleInventoryReservedEventTests {

        @Test
        @DisplayName("should create and save ORDER_CONFIRMED notification when event is new")
        void shouldCreateAndSaveOrderConfirmedNotification() {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            InventoryReservedEvent event = new InventoryReservedEvent(eventId, orderId, Instant.now(), 1);

            when(notificationRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            notificationService.handleInventoryReservedEvent(event);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());

            Notification saved = captor.getValue();
            assertThat(saved.getEventId()).isEqualTo(eventId);
            assertThat(saved.getOrderId()).isEqualTo(orderId);
            assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CONFIRMED);
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(saved.getRecipient()).isEqualTo("customer x");
            assertThat(saved.getMessage()).isEqualTo("Your order " + orderId + " has been confirmed.");
        }

        @Test
        @DisplayName("should skip processing when event has already been processed")
        void shouldSkipWhenEventAlreadyProcessed() {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            InventoryReservedEvent event = new InventoryReservedEvent(eventId, orderId, Instant.now(), 1);

            Notification existingNotification = Notification.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .orderId(orderId)
                    .type(NotificationType.ORDER_CONFIRMED)
                    .status(NotificationStatus.PENDING)
                    .build();

            when(notificationRepository.findByEventId(eventId)).thenReturn(Optional.of(existingNotification));

            notificationService.handleInventoryReservedEvent(event);

            verify(notificationRepository, never()).save(any(Notification.class));
        }
    }

    @Nested
    @DisplayName("handleInventoryRejectedEvent")
    class HandleInventoryRejectedEventTests {

        @Test
        @DisplayName("should create and save ORDER_CANCELLED notification when event is new")
        void shouldCreateAndSaveOrderCancelledNotification() {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            InventoryRejectedEvent event = new InventoryRejectedEvent(
                    eventId, orderId, productId, 2, 0, "OUT_OF_STOCK", Instant.now(), 1
            );

            when(notificationRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            notificationService.handleInventoryRejectedEvent(event);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());

            Notification saved = captor.getValue();
            assertThat(saved.getEventId()).isEqualTo(eventId);
            assertThat(saved.getOrderId()).isEqualTo(orderId);
            assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CANCELLED);
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(saved.getRecipient()).isEqualTo("customer x");
            assertThat(saved.getMessage()).isEqualTo("Your order " + orderId + " has been cancelled.");
        }

        @Test
        @DisplayName("should skip processing when event has already been processed")
        void shouldSkipWhenEventAlreadyProcessed() {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            InventoryRejectedEvent event = new InventoryRejectedEvent(
                    eventId, orderId, productId, 2, 0, "OUT_OF_STOCK", Instant.now(), 1
            );

            Notification existingNotification = Notification.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .orderId(orderId)
                    .type(NotificationType.ORDER_CANCELLED)
                    .status(NotificationStatus.PENDING)
                    .build();

            when(notificationRepository.findByEventId(eventId)).thenReturn(Optional.of(existingNotification));

            notificationService.handleInventoryRejectedEvent(event);

            verify(notificationRepository, never()).save(any(Notification.class));
        }
    }
}
