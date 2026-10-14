package com.clinic.notification.integration;

import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryTest extends IntegrationTestBase {

    private Notification notification(UUID eventId, NotificationStatus status) {
        return Notification.builder().eventId(eventId).appointmentId(1L).patientId(101L).doctorId(201L)
                .message("msg").status(status).attempts(0).createdAt(Instant.now()).build();
    }

    @Test
    void findByEventId_returnsTheStoredNotification() {
        // given
        UUID eventId = UUID.randomUUID();
        repository.save(notification(eventId, NotificationStatus.PENDING));

        // when / then
        assertThat(repository.findByEventId(eventId)).isPresent();
        assertThat(repository.findByEventId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void save_sameEventIdTwice_isRejectedByTheUniqueConstraint() {
        // given
        UUID eventId = UUID.randomUUID();
        repository.saveAndFlush(notification(eventId, NotificationStatus.PENDING));

        // when / then
        assertThatThrownBy(() -> repository.saveAndFlush(notification(eventId, NotificationStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByStatus_returnsOnlyMatchingNotifications() {
        // given
        repository.save(notification(UUID.randomUUID(), NotificationStatus.SENT));
        repository.save(notification(UUID.randomUUID(), NotificationStatus.DEAD_LETTERED));
        repository.save(notification(UUID.randomUUID(), NotificationStatus.DEAD_LETTERED));

        // when
        var page = repository.findByStatus(NotificationStatus.DEAD_LETTERED, PageRequest.of(0, 10));

        // then
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Notification::getStatus).containsOnly(NotificationStatus.DEAD_LETTERED);
    }
}
