package com.clinic.notification.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventParserTest {

    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final EventParser parser = new EventParser(mapper);

    private static String json(String eventId, String appointmentId, String patientId, String doctorId,
                               String startTime) {
        StringBuilder sb = new StringBuilder("{\"eventType\":\"AppointmentBooked\"");
        if (eventId != null) sb.append(",\"eventId\":\"").append(eventId).append('"');
        sb.append(",\"occurredAt\":\"2026-01-01T10:00:00Z\"");
        if (appointmentId != null) sb.append(",\"appointmentId\":").append(appointmentId);
        if (patientId != null) sb.append(",\"patientId\":").append(patientId);
        if (doctorId != null) sb.append(",\"doctorId\":").append(doctorId);
        if (startTime != null) sb.append(",\"startTime\":\"").append(startTime).append('"');
        return sb.append('}').toString();
    }

    @Test
    @DisplayName("Given a valid payload, when parsed, then all fields are mapped")
    void parse_validPayload() {
        // given
        String payload = json(ID, "100", "7", "3", "2026-02-01T09:30:00");

        // when
        AppointmentBookedEvent event = parser.parse(payload);

        // then
        assertThat(event.eventId()).isEqualTo(UUID.fromString(ID));
        assertThat(event.eventType()).isEqualTo("AppointmentBooked");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(event.appointmentId()).isEqualTo(100L);
        assertThat(event.patientId()).isEqualTo(7L);
        assertThat(event.doctorId()).isEqualTo(3L);
        assertThat(event.startTime()).isEqualTo(LocalDateTime.of(2026, 2, 1, 9, 30));
    }

    @Test
    @DisplayName("Given a payload with unknown fields, when parsed, then they are ignored")
    void parse_unknownFieldsIgnored() {
        // given
        String payload = json(ID, "100", "7", "3", "2026-02-01T09:30:00").replace("}", ",\"extra\":\"x\"}");

        // when
        AppointmentBookedEvent event = parser.parse(payload);

        // then
        assertThat(event.appointmentId()).isEqualTo(100L);
    }

    @ParameterizedTest(name = "[{index}] payload=''{0}''")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    @DisplayName("Given a null, empty or blank payload, when parsed, then InvalidEventException is thrown")
    void parse_blankPayload_throws(String payload) {
        // when / then
        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Empty event payload");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{\"eventId\":", "[1,2,3]"})
    @DisplayName("Given malformed JSON, when parsed, then InvalidEventException with a cause is thrown")
    void parse_malformedJson_throws(String payload) {
        // when / then
        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("not valid JSON")
                .hasCauseInstanceOf(tools.jackson.core.JacksonException.class);
    }

    @Test
    @DisplayName("Given an eventId that is not a UUID, when parsed, then InvalidEventException is thrown")
    void parse_badUuid_throws() {
        // given
        String payload = json("not-a-uuid", "100", "7", "3", "2026-02-01T09:30:00");

        // when / then
        assertThatThrownBy(() -> parser.parse(payload)).isInstanceOf(InvalidEventException.class);
    }

    @Test
    @DisplayName("Given an unparseable startTime, when parsed, then InvalidEventException is thrown")
    void parse_badStartTime_throws() {
        // given
        String payload = json(ID, "100", "7", "3", "yesterday-ish");

        // when / then
        assertThatThrownBy(() -> parser.parse(payload)).isInstanceOf(InvalidEventException.class);
    }

    @Test
    @DisplayName("Given a payload missing eventId, when parsed, then 'missing required fields' is thrown")
    void parse_missingEventId() {
        assertMissingRequired(json(null, "100", "7", "3", "2026-02-01T09:30:00"));
    }

    @Test
    @DisplayName("Given a payload missing appointmentId, when parsed, then 'missing required fields' is thrown")
    void parse_missingAppointmentId() {
        assertMissingRequired(json(ID, null, "7", "3", "2026-02-01T09:30:00"));
    }

    @Test
    @DisplayName("Given a payload missing patientId, when parsed, then 'missing required fields' is thrown")
    void parse_missingPatientId() {
        assertMissingRequired(json(ID, "100", null, "3", "2026-02-01T09:30:00"));
    }

    @Test
    @DisplayName("Given a payload missing doctorId, when parsed, then 'missing required fields' is thrown")
    void parse_missingDoctorId() {
        assertMissingRequired(json(ID, "100", "7", null, "2026-02-01T09:30:00"));
    }

    @Test
    @DisplayName("Given a payload missing startTime, when parsed, then 'missing required fields' is thrown")
    void parse_missingStartTime() {
        assertMissingRequired(json(ID, "100", "7", "3", null));
    }

    @Test
    @DisplayName("Given an empty JSON object, when parsed, then 'missing required fields' is thrown")
    void parse_emptyObject() {
        assertMissingRequired("{}");
    }

    private void assertMissingRequired(String payload) {
        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Event is missing required fields");
    }
}
