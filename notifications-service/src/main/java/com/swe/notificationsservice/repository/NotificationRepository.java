package com.swe.notificationsservice.repository;

import com.swe.notificationsservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByEventId(UUID eventId);
}
