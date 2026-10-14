package com.clinic.notification.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void notificationStatus_hasTheThreeLifecycleStates() {
        // given / when / then
        assertThat(NotificationStatus.values())
                .containsExactly(NotificationStatus.PENDING, NotificationStatus.SENT, NotificationStatus.DEAD_LETTERED);
    }

    @Test
    void notification_builderKeepsAllFields() {
        // given
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-01T08:00:00Z");

        // when
        Notification n = Notification.builder().id(1L).eventId(eventId).appointmentId(7L).patientId(101L).doctorId(201L)
                .message("msg").status(NotificationStatus.PENDING).attempts(2).createdAt(now).lastError("boom").build();

        // then
        assertThat(n.getEventId()).isEqualTo(eventId);
        assertThat(n.getAppointmentId()).isEqualTo(7L);
        assertThat(n.getAttempts()).isEqualTo(2);
        assertThat(n.getSentAt()).isNull();
        assertThat(n.getLastError()).isEqualTo("boom");
    }
}
