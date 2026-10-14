package com.clinic.notification.service;

import com.clinic.notification.event.AppointmentBookedEvent;
import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;
import com.clinic.notification.repository.NotificationRepository;
import com.clinic.notification.sender.NotificationSender;
import com.clinic.notification.sender.SendFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.clinic.notification.testutil.TestData.EVENT_ID;
import static com.clinic.notification.testutil.TestData.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real service + processor wired to an in-memory repository and a counting sender. No Spring, no database. */
class DuplicateEventProcessingTest {

    private final Map<UUID, Notification> store = new LinkedHashMap<>();
    private final List<UUID> sends = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private boolean failNextSend;

    private NotificationProcessor processor;

    @BeforeEach
    void setUp() {
        NotificationRepository repository = mock(NotificationRepository.class);
        when(repository.findByEventId(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.<UUID>getArgument(0))));
        when(repository.findById(anyLong())).thenAnswer(i -> store.values().stream()
                .filter(n -> n.getId().equals(i.<Long>getArgument(0))).findFirst());
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            n.setId(ids.incrementAndGet());
            store.put(n.getEventId(), n);
            return n;
        });

        NotificationSender sender = notification -> {
            if (failNextSend) {
                failNextSend = false;
                throw new SendFailedException("down");
            }
            sends.add(notification.getEventId());
        };
        processor = new NotificationProcessor(new NotificationService(repository), sender);
    }

    @Test
    @DisplayName("Given the same event, when processed twice, then one notification is created and one send happens")
    void sameEventTwice_createsOneNotificationAndOneSend() {
        // given
        AppointmentBookedEvent event = event();

        // when
        processor.process(event);
        processor.process(event);

        // then
        assertThat(store).hasSize(1);
        assertThat(store.get(EVENT_ID).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(store.get(EVENT_ID).getAttempts()).isEqualTo(1);
        assertThat(sends).containsExactly(EVENT_ID);
    }

    @Test
    @DisplayName("Given the same event, when processed many times, then still one notification and one send")
    void sameEventManyTimes_stillOneNotificationAndOneSend() {
        // given
        AppointmentBookedEvent event = event();

        // when
        for (int i = 0; i < 5; i++) {
            processor.process(event);
        }

        // then
        assertThat(store).hasSize(1);
        assertThat(sends).hasSize(1);
    }

    @Test
    @DisplayName("Given the first delivery failed to send, when redelivered, then still one notification and one successful send")
    void failedFirstDelivery_thenRedelivery_oneNotificationOneSend() {
        // given
        AppointmentBookedEvent event = event();
        failNextSend = true;
        assertThatThrownBy(() -> processor.process(event)).isInstanceOf(SendFailedException.class);

        // when
        processor.process(event);

        // then
        assertThat(store).hasSize(1);
        assertThat(store.get(EVENT_ID).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(store.get(EVENT_ID).getAttempts()).isEqualTo(2);
        assertThat(sends).containsExactly(EVENT_ID);
    }

    @Test
    @DisplayName("Given two different events, when each is processed, then two notifications and two sends")
    void differentEvents_createSeparateNotificationsAndSends() {
        // given
        AppointmentBookedEvent first = event();
        AppointmentBookedEvent second = new AppointmentBookedEvent(UUID.randomUUID(), first.eventType(),
                first.occurredAt(), 101L, first.patientId(), first.doctorId(), first.startTime());

        // when
        processor.process(first);
        processor.process(second);

        // then
        assertThat(store).hasSize(2);
        assertThat(sends).containsExactly(first.eventId(), second.eventId());
    }
}
