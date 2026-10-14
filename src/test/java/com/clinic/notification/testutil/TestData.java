package com.clinic.notification.testutil;

import com.clinic.notification.event.AppointmentBookedEvent;
import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public final class TestData {

    public static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private TestData() {
    }

    public static AppointmentBookedEvent event() {
        return new AppointmentBookedEvent(EVENT_ID, "AppointmentBooked", Instant.parse("2026-01-01T10:00:00Z"),
                100L, 7L, 3L, LocalDateTime.of(2026, 2, 1, 9, 30));
    }

    public static Notification notification(Long id, NotificationStatus status) {
        return Notification.builder()
                .id(id)
                .eventId(EVENT_ID)
                .appointmentId(100L)
                .patientId(7L)
                .doctorId(3L)
                .message("msg")
                .status(status)
                .attempts(0)
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
    }
}
