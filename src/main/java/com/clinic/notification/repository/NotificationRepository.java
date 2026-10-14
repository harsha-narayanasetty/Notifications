package com.clinic.notification.repository;

import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByEventId(UUID eventId);

    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);
}
