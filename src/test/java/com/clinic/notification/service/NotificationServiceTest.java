package com.clinic.notification.service;

import com.clinic.notification.dto.NotificationResponse;
import com.clinic.notification.dto.PageResponse;
import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;
import com.clinic.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.clinic.notification.testutil.TestData.EVENT_ID;
import static com.clinic.notification.testutil.TestData.event;
import static com.clinic.notification.testutil.TestData.notification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository repository;

    @InjectMocks
    NotificationService service;

    @Test
    @DisplayName("Given a new event, when recordIfAbsent, then a PENDING notification is saved with a built message")
    void recordIfAbsent_newEvent_savesPendingNotification() {
        // given
        when(repository.findByEventId(EVENT_ID)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        // when
        Notification result = service.recordIfAbsent(event());

        // then
        assertThat(result.getEventId()).isEqualTo(EVENT_ID);
        assertThat(result.getAppointmentId()).isEqualTo(100L);
        assertThat(result.getPatientId()).isEqualTo(7L);
        assertThat(result.getDoctorId()).isEqualTo(3L);
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(result.getAttempts()).isZero();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getMessage())
                .isEqualTo("Appointment booked for patient P7 with doctor D3 at 2026-02-01T09:30.");
    }

    @Test
    @DisplayName("Given an already stored event, when recordIfAbsent, then the existing row is returned and nothing is saved")
    void recordIfAbsent_existingEvent_returnsExisting() {
        // given
        Notification existing = notification(1L, NotificationStatus.SENT);
        when(repository.findByEventId(EVENT_ID)).thenReturn(Optional.of(existing));

        // when
        Notification result = service.recordIfAbsent(event());

        // then
        assertThat(result).isSameAs(existing);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Given a stored notification, when getByEventId, then it is returned")
    void getByEventId_found() {
        // given
        Notification existing = notification(1L, NotificationStatus.PENDING);
        when(repository.findByEventId(EVENT_ID)).thenReturn(Optional.of(existing));

        // when / then
        assertThat(service.getByEventId(EVENT_ID)).isSameAs(existing);
    }

    @Test
    @DisplayName("Given no notification, when getByEventId, then NoSuchElementException is thrown")
    void getByEventId_missing_throws() {
        // given
        when(repository.findByEventId(EVENT_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.getByEventId(EVENT_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(EVENT_ID.toString());
    }

    @Test
    @DisplayName("Given a pending notification, when markSent, then status is SENT, attempts incremented, error cleared")
    void markSent_updatesNotification() {
        // given
        Notification n = notification(1L, NotificationStatus.PENDING);
        n.setAttempts(2);
        n.setLastError("boom");
        when(repository.findById(1L)).thenReturn(Optional.of(n));

        // when
        service.markSent(1L);

        // then
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getSentAt()).isNotNull();
        assertThat(n.getAttempts()).isEqualTo(3);
        assertThat(n.getLastError()).isNull();
    }

    @Test
    @DisplayName("Given an unknown id, when markSent, then NoSuchElementException is thrown")
    void markSent_unknownId_throws() {
        // given
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.markSent(99L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Given a pending notification, when recordFailure, then attempts incremented and error stored, status unchanged")
    void recordFailure_updatesAttemptsAndError() {
        // given
        Notification n = notification(1L, NotificationStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(n));

        // when
        service.recordFailure(1L, "send failed");

        // then
        assertThat(n.getAttempts()).isEqualTo(1);
        assertThat(n.getLastError()).isEqualTo("send failed");
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("Given an error longer than 500 chars, when recordFailure, then it is truncated to 500")
    void recordFailure_longError_truncated() {
        // given
        Notification n = notification(1L, NotificationStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(n));

        // when
        service.recordFailure(1L, "x".repeat(600));

        // then
        assertThat(n.getLastError()).hasSize(500);
    }

    @Test
    @DisplayName("Given a null error, when recordFailure, then lastError stays null")
    void recordFailure_nullError() {
        // given
        Notification n = notification(1L, NotificationStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(n));

        // when
        service.recordFailure(1L, null);

        // then
        assertThat(n.getLastError()).isNull();
        assertThat(n.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Given an unknown id, when recordFailure, then NoSuchElementException is thrown")
    void recordFailure_unknownId_throws() {
        // given
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.recordFailure(99L, "e")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Given a stored notification, when markDeadLettered, then status is DEAD_LETTERED with the error")
    void markDeadLettered_existing() {
        // given
        Notification n = notification(1L, NotificationStatus.PENDING);
        when(repository.findByEventId(EVENT_ID)).thenReturn(Optional.of(n));

        // when
        service.markDeadLettered(EVENT_ID, "retries exhausted");

        // then
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
        assertThat(n.getLastError()).isEqualTo("retries exhausted");
    }

    @Test
    @DisplayName("Given no stored notification, when markDeadLettered, then nothing happens and no exception is thrown")
    void markDeadLettered_missing_isNoOp() {
        // given
        when(repository.findByEventId(EVENT_ID)).thenReturn(Optional.empty());

        // when / then
        service.markDeadLettered(EVENT_ID, "err");
    }

    @Test
    @DisplayName("Given no status filter, when list, then all notifications are queried sorted by id desc")
    void list_noStatus_usesFindAll() {
        // given
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification(1L, NotificationStatus.SENT))));

        // when
        PageResponse<NotificationResponse> result = service.list(null, 0, 20);

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(NotificationStatus.SENT);
        verify(repository, never()).findByStatus(any(), any());
    }

    @Test
    @DisplayName("Given a status filter, when list, then findByStatus is used")
    void list_withStatus_usesFindByStatus() {
        // given
        when(repository.findByStatus(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification(2L, NotificationStatus.PENDING))));

        // when
        PageResponse<NotificationResponse> result = service.list(NotificationStatus.PENDING, 0, 10);

        // then
        verify(repository).findByStatus(any(), any(Pageable.class));
        verify(repository, never()).findAll(any(Pageable.class));
        assertThat(result.content()).extracting(NotificationResponse::id).containsExactly(2L);
    }

    @Test
    @DisplayName("Given a negative page and oversized size, when list, then page is 0 and size capped at 100")
    void list_clampsPageAndSize() {
        // given
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        // when
        service.list(null, -5, 1000);

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Given a zero size, when list, then size is raised to 1")
    void list_zeroSize_raisedToOne() {
        // given
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        // when
        service.list(null, 0, 0);

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }
}
