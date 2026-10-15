package com.clinic.notification.service;

import com.clinic.notification.event.AppointmentBookedEvent;
import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;
import com.clinic.notification.sender.NotificationSender;
import com.clinic.notification.sender.SendFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.NoSuchElementException;

import static com.clinic.notification.testutil.TestData.EVENT_ID;
import static com.clinic.notification.testutil.TestData.event;
import static com.clinic.notification.testutil.TestData.notification;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationProcessorTest {

    @Mock
    NotificationService service;

    @Mock
    NotificationSender sender;

    @InjectMocks
    NotificationProcessor processor;

    @Test
    @DisplayName("Given a new event, when processed, then it is stored, sent, and marked SENT in that order")
    void process_newEvent_sendsAndMarksSent() {
        // given
        AppointmentBookedEvent event = event();
        Notification n = notification(1L, NotificationStatus.PENDING);
        when(service.recordIfAbsent(event)).thenReturn(n);

        // when
        processor.process(event);

        // then
        InOrder order = inOrder(service, sender);
        order.verify(service).recordIfAbsent(event);
        order.verify(sender).send(n);
        order.verify(service).markSent(1L);
    }

    @Test
    @DisplayName("Given a notification already SENT, when the event is redelivered, then nothing is sent again")
    void process_alreadySent_isIgnored() {
        // given
        AppointmentBookedEvent event = event();
        when(service.recordIfAbsent(event)).thenReturn(notification(1L, NotificationStatus.SENT));

        // when
        processor.process(event);

        // then
        verifyNoInteractions(sender);
        verify(service, never()).markSent(1L);
    }

    @Test
    @DisplayName("Given a PENDING notification from an earlier failed attempt, when reprocessed, then the send is retried")
    void process_existingPending_retriesSend() {
        // given
        AppointmentBookedEvent event = event();
        Notification n = notification(1L, NotificationStatus.PENDING);
        when(service.recordIfAbsent(event)).thenReturn(n);

        // when
        processor.process(event);

        // then
        verify(sender).send(n);
        verify(service).markSent(1L);
    }

    @Test
    @DisplayName("Given the sender fails, when processed, then the failure is recorded and the exception rethrown")
    void process_sendFails_recordsFailureAndRethrows() {
        // given
        AppointmentBookedEvent event = event();
        Notification n = notification(1L, NotificationStatus.PENDING);
        when(service.recordIfAbsent(event)).thenReturn(n);
        doThrow(new SendFailedException("down")).when(sender).send(n);

        // when / then
        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(SendFailedException.class)
                .hasMessage("down");
        verify(service).recordFailure(1L, "down");
        verify(service, never()).markSent(1L);
    }

    @Test
    @DisplayName("Given a concurrent duplicate hits the unique constraint, when processed, then the stored row is loaded and sent")
    void process_duplicateRace_loadsExistingAndSends() {
        // given
        AppointmentBookedEvent event = event();
        Notification n = notification(1L, NotificationStatus.PENDING);
        when(service.recordIfAbsent(event)).thenThrow(new DataIntegrityViolationException("dup"));
        when(service.getByEventId(EVENT_ID)).thenReturn(n);

        // when
        processor.process(event);

        // then
        verify(sender).send(n);
        verify(service).markSent(1L);
    }

    @Test
    @DisplayName("Given a duplicate race where the winner already SENT it, when processed, then nothing is sent")
    void process_duplicateRaceAlreadySent_isIgnored() {
        // given
        AppointmentBookedEvent event = event();
        when(service.recordIfAbsent(event)).thenThrow(new DataIntegrityViolationException("dup"));
        when(service.getByEventId(EVENT_ID)).thenReturn(notification(1L, NotificationStatus.SENT));

        // when
        processor.process(event);

        // then
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("Given a duplicate error but no stored row, when processed, then the lookup failure propagates")
    void process_duplicateButRowMissing_propagates() {
        // given
        AppointmentBookedEvent event = event();
        when(service.recordIfAbsent(event)).thenThrow(new DataIntegrityViolationException("dup"));
        when(service.getByEventId(EVENT_ID)).thenThrow(new NoSuchElementException("missing"));

        // when / then
        assertThatThrownBy(() -> processor.process(event)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("Given the sender throws an unexpected runtime error, when processed, then it is recorded too and rethrown")
    void process_unexpectedSenderError_isRecordedAndRethrown() {
        // given
        AppointmentBookedEvent event = event();
        Notification n = notification(1L, NotificationStatus.PENDING);
        when(service.recordIfAbsent(event)).thenReturn(n);
        doThrow(new IllegalStateException("provider timeout")).when(sender).send(n);

        // when / then
        assertThatThrownBy(() -> processor.process(event)).isInstanceOf(IllegalStateException.class);
        verify(service).recordFailure(1L, "provider timeout");
        verify(service, never()).markSent(1L);
    }

    @Test
    @DisplayName("Given a sender error without a message, when processed, then the exception text is recorded")
    void process_senderErrorWithoutMessage_recordsTheExceptionText() {
        // given
        AppointmentBookedEvent event = event();
        Notification n = notification(1L, NotificationStatus.PENDING);
        when(service.recordIfAbsent(event)).thenReturn(n);
        doThrow(new NullPointerException()).when(sender).send(n);

        // when / then
        assertThatThrownBy(() -> processor.process(event)).isInstanceOf(NullPointerException.class);
        verify(service).recordFailure(1L, "java.lang.NullPointerException");
    }
}
