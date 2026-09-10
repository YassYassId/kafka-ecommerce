package com.swe.notificationsservice.service;

import com.swe.notificationsservice.entity.Notification;
import com.swe.notificationsservice.entity.NotificationStatus;
import com.swe.notificationsservice.entity.NotificationType;
import com.swe.notificationsservice.event.InventoryRejectedEvent;
import com.swe.notificationsservice.event.InventoryReservedEvent;
import com.swe.notificationsservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void handleInventoryReservedEvent(InventoryReservedEvent event) {

        if (notificationRepository.findByEventId(event.eventId()).isPresent()) {
            // Event has already been processed, skip handling
            return;
        }
        Notification notification = Notification.builder()
                .eventId(event.eventId())
                .orderId(event.orderId())
                .type(NotificationType.ORDER_CONFIRMED)
                .recipient("customer x")
                .message("Your order " + event.orderId() + " has been confirmed.")
                .status(NotificationStatus.PENDING)
                .build();

        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void handleInventoryRejectedEvent(InventoryRejectedEvent event) {

        if (notificationRepository.findByEventId(event.eventId()).isPresent()) {
            // Event has already been processed, skip handling
            return;
        }

        Notification notification = Notification.builder()
                .eventId(event.eventId())
                .orderId(event.orderId())
                .type(NotificationType.ORDER_CANCELLED)
                .recipient("customer x")
                .message("Your order " + event.orderId() + " has been cancelled.")
                .status(NotificationStatus.PENDING)
                .build();

        notificationRepository.save(notification);
    }
}
