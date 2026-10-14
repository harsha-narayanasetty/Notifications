package com.clinic.notification.service;

import com.clinic.notification.dto.NotificationResponse;
import com.clinic.notification.dto.PageResponse;
import com.clinic.notification.event.AppointmentBookedEvent;
import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;
import com.clinic.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Small, separate transactions. Sending happens OUTSIDE them (see NotificationProcessor). */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Idempotent store. If this event was already stored, return the existing row instead of
     * creating another. A concurrent duplicate that slips past the lookup hits the UNIQUE
     * constraint on event_id; the caller handles that (see NotificationProcessor).
     */
    @Transactional
    public Notification recordIfAbsent(AppointmentBookedEvent event) {
        return notificationRepository.findByEventId(event.eventId())
                .orElseGet(() -> notificationRepository.saveAndFlush(Notification.builder()
                        .eventId(event.eventId())
                        .appointmentId(event.appointmentId())
                        .patientId(event.patientId())
                        .doctorId(event.doctorId())
                        .message(buildMessage(event))
                        .status(NotificationStatus.PENDING)
                        .attempts(0)
                        .createdAt(Instant.now())
                        .build()));
    }

    @Transactional(readOnly = true)
    public Notification getByEventId(UUID eventId) {
        return notificationRepository.findByEventId(eventId)
                .orElseThrow(() -> new NoSuchElementException("No notification for event " + eventId));
    }

    @Transactional
    public void markSent(Long id) {
        Notification n = notificationRepository.findById(id).orElseThrow();
        n.setStatus(NotificationStatus.SENT);
        n.setSentAt(Instant.now());
        n.setAttempts(n.getAttempts() + 1);
        n.setLastError(null);
    }

    @Transactional
    public void recordFailure(Long id, String error) {
        Notification n = notificationRepository.findById(id).orElseThrow();
        n.setAttempts(n.getAttempts() + 1);
        n.setLastError(truncate(error));
    }

    /** Called when retries are exhausted, just before the event is published to the dead-letter topic. */
    @Transactional
    public void markDeadLettered(UUID eventId, String error) {
        notificationRepository.findByEventId(eventId).ifPresent(n -> {
            n.setStatus(NotificationStatus.DEAD_LETTERED);
            n.setLastError(truncate(error));
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(NotificationStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<Notification> result = (status == null)
                ? notificationRepository.findAll(pageable)
                : notificationRepository.findByStatus(status, pageable);
        return PageResponse.from(result.map(NotificationResponse::from));
    }

    private static String buildMessage(AppointmentBookedEvent e) {
        return "Appointment booked for patient P" + e.patientId()
                + " with doctor D" + e.doctorId() + " at " + e.startTime() + ".";
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
