package com.clinic.notification.integration;

import com.clinic.notification.event.EventParser;
import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;
import com.clinic.notification.sender.SendFailedException;
import com.clinic.notification.service.NotificationProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationIdempotencyTest extends IntegrationTestBase {

    @Autowired NotificationProcessor processor;
    @Autowired EventParser parser;

    private static String payload(UUID eventId) {
        return """
                {"eventId":"%s","eventType":"AppointmentBooked","occurredAt":"2026-11-01T08:00:00Z",
                 "appointmentId":1,"patientId":101,"doctorId":201,"startTime":"2026-11-02T10:00:00"}"""
                .formatted(eventId);
    }

    @Test
    @DisplayName("The same event processed twice creates one notification")
    void sameEventTwiceCreatesOneNotification() {
        UUID eventId = UUID.randomUUID();

        processor.process(parser.parse(payload(eventId)));
        processor.process(parser.parse(payload(eventId)));

        List<Notification> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(all.get(0).getMessage())
                .isEqualTo("Appointment booked for patient P101 with doctor D201 at 2026-11-02T10:00.");
        assertThat(sender.sentEventIds).as("the patient is notified only once").containsExactly(eventId);
    }

    @Test
    @DisplayName("The same event delivered at the same moment by several consumers still creates one notification")
    void concurrentDuplicatesCreateOneNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        int deliveries = 10;
        CountDownLatch ready = new CountDownLatch(deliveries);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(deliveries);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < deliveries; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                processor.process(parser.parse(payload(eventId)));
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get();   // no delivery may blow up on the unique-constraint race
        }
        pool.shutdown();

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByEventId(eventId)).get()
                .extracting(Notification::getStatus).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("A failed send is retried on redelivery and still ends with one SENT notification")
    void failedSendIsRecordedThenRetriedWithoutDuplicates() {
        UUID eventId = UUID.randomUUID();
        sender.failuresToSimulate.set(1);

        assertThatThrownBy(() -> processor.process(parser.parse(payload(eventId))))
                .isInstanceOf(SendFailedException.class);
        Notification afterFailure = repository.findByEventId(eventId).orElseThrow();
        assertThat(afterFailure.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(afterFailure.getAttempts()).isEqualTo(1);
        assertThat(afterFailure.getLastError()).contains("simulated failure");

        processor.process(parser.parse(payload(eventId)));   // what the Kafka retry does

        assertThat(repository.count()).isEqualTo(1);
        Notification afterRetry = repository.findByEventId(eventId).orElseThrow();
        assertThat(afterRetry.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(afterRetry.getAttempts()).isEqualTo(2);
        assertThat(sender.sentEventIds).containsExactly(eventId);
    }

    @Test
    @DisplayName("Different events create different notifications")
    void differentEventsCreateSeparateNotifications() {
        processor.process(parser.parse(payload(UUID.randomUUID())));
        processor.process(parser.parse(payload(UUID.randomUUID())));

        assertThat(repository.count()).isEqualTo(2);
    }
}
