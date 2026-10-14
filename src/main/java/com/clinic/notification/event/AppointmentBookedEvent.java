package com.clinic.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** This service's own copy of the event contract. Unknown fields are ignored so the producer can evolve. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppointmentBookedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long appointmentId,
        Long patientId,
        Long doctorId,
        LocalDateTime startTime
) {
}
