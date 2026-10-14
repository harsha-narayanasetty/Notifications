package com.clinic.notification.sender;

import com.clinic.notification.model.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/** Pretends to send an email/SMS and fails randomly, to exercise the retry and dead-letter paths. */
@Slf4j
@Component
public class StubNotificationSender implements NotificationSender {

    private final double failureRate;

    public StubNotificationSender(@Value("${notification.sender.failure-rate:0.3}") double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public void send(Notification notification) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new SendFailedException("Stub sender: simulated delivery failure for event "
                    + notification.getEventId());
        }
        log.info("STUB SEND -> {}", notification.getMessage());
    }
}
