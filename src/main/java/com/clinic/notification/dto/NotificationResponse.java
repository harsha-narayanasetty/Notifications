package com.clinic.notification.dto;

import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        Long id,
        UUID eventId,
        Long appointmentId,
        Long patientId,
        Long doctorId,
        String message,
        NotificationStatus status,
        int attempts,
        Instant createdAt,
        Instant sentAt,
        String lastError
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventId(), n.getAppointmentId(), n.getPatientId(),
                n.getDoctorId(), n.getMessage(), n.getStatus(), n.getAttempts(), n.getCreatedAt(),
                n.getSentAt(), n.getLastError());
    }
}
