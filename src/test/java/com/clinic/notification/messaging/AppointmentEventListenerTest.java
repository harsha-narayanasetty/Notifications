package com.clinic.notification.messaging;

import com.clinic.notification.event.AppointmentBookedEvent;
import com.clinic.notification.event.EventParser;
import com.clinic.notification.event.InvalidEventException;
import com.clinic.notification.sender.SendFailedException;
import com.clinic.notification.service.NotificationProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.clinic.notification.testutil.TestData.event;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentEventListenerTest {

    @Mock
    EventParser parser;

    @Mock
    NotificationProcessor processor;

    @InjectMocks
    AppointmentEventListener listener;

    @Test
    @DisplayName("Given a valid payload, when received, then it is parsed and handed to the processor")
    void onAppointmentBooked_validPayload() {
        // given
        AppointmentBookedEvent event = event();
        when(parser.parse("payload")).thenReturn(event);

        // when
        listener.onAppointmentBooked("payload");

        // then
        verify(processor).process(event);
    }

    @Test
    @DisplayName("Given an invalid payload, when received, then the exception propagates and nothing is processed")
    void onAppointmentBooked_invalidPayload_propagates() {
        // given
        when(parser.parse("bad")).thenThrow(new InvalidEventException("bad"));

        // when / then
        assertThatThrownBy(() -> listener.onAppointmentBooked("bad")).isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("Given the processor fails, when received, then the exception propagates to the Kafka error handler")
    void onAppointmentBooked_processorFails_propagates() {
        // given
        AppointmentBookedEvent event = event();
        when(parser.parse("payload")).thenReturn(event);
        doThrow(new SendFailedException("down")).when(processor).process(event);

        // when / then
        assertThatThrownBy(() -> listener.onAppointmentBooked("payload")).isInstanceOf(SendFailedException.class);
    }
}
