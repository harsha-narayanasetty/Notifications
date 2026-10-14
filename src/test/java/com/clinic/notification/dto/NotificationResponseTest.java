package com.clinic.notification.dto;

import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static com.clinic.notification.testutil.TestData.EVENT_ID;
import static com.clinic.notification.testutil.TestData.notification;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationResponseTest {

    @Test
    @DisplayName("Given a notification entity, when mapped, then every field is copied to the response")
    void from_mapsAllFields() {
        // given
        Notification n = notification(5L, NotificationStatus.SENT);
        n.setAttempts(2);
        n.setSentAt(Instant.parse("2026-01-01T10:05:00Z"));
        n.setLastError("old error");

        // when
        NotificationResponse r = NotificationResponse.from(n);

        // then
        assertThat(r.id()).isEqualTo(5L);
        assertThat(r.eventId()).isEqualTo(EVENT_ID);
        assertThat(r.appointmentId()).isEqualTo(100L);
        assertThat(r.patientId()).isEqualTo(7L);
        assertThat(r.doctorId()).isEqualTo(3L);
        assertThat(r.message()).isEqualTo("msg");
        assertThat(r.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(r.createdAt()).isEqualTo(n.getCreatedAt());
        assertThat(r.sentAt()).isEqualTo(n.getSentAt());
        assertThat(r.lastError()).isEqualTo("old error");
    }

    @Test
    @DisplayName("Given a Spring Page, when mapped to PageResponse, then content and paging metadata are copied")
    void pageResponse_from_copiesMetadata() {
        // given
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        // when
        PageResponse<String> r = PageResponse.from(page);

        // then
        assertThat(r.content()).containsExactly("a", "b");
        assertThat(r.page()).isEqualTo(1);
        assertThat(r.size()).isEqualTo(2);
        assertThat(r.totalElements()).isEqualTo(5);
        assertThat(r.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("Given an empty Page, when mapped, then content is empty and totals are zero")
    void pageResponse_from_emptyPage() {
        // given
        PageImpl<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        // when
        PageResponse<String> r = PageResponse.from(page);

        // then
        assertThat(r.content()).isEmpty();
        assertThat(r.totalElements()).isZero();
        assertThat(r.totalPages()).isZero();
    }
}
