package com.clinic.notification.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class EventParser {

    private final ObjectMapper objectMapper;   // Jackson 3 (Spring Boot 4)

    public AppointmentBookedEvent parse(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidEventException("Empty event payload");
        }
        AppointmentBookedEvent event;
        try {
            event = objectMapper.readValue(payload, AppointmentBookedEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("Event is not valid JSON for AppointmentBooked", e);
        }
        if (event.eventId() == null || event.appointmentId() == null || event.patientId() == null
                || event.doctorId() == null || event.startTime() == null) {
            throw new InvalidEventException("Event is missing required fields");
        }
        return event;
    }
}
