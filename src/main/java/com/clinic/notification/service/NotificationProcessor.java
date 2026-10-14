package com.clinic.notification.service;

import com.clinic.notification.event.AppointmentBookedEvent;
import com.clinic.notification.model.Notification;
import com.clinic.notification.model.NotificationStatus;
import com.clinic.notification.sender.NotificationSender;
import com.clinic.notification.sender.SendFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Handles one event. Deliberately NOT @Transactional: the notification is committed first, the
 * (slow, failure-prone) send happens outside any database transaction, and the result is recorded
 * afterwards. If the send throws, the Kafka error handler retries the whole method; the second run
 * finds the stored row and only repeats the send.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationProcessor {

    private final NotificationService notificationService;
    private final NotificationSender sender;

    public void process(AppointmentBookedEvent event) {
        Notification notification;
        try {
            notification = notificationService.recordIfAbsent(event);
        } catch (DataIntegrityViolationException duplicate) {
            // Two deliveries of the same event raced; the other one stored it first.
            notification = notificationService.getByEventId(event.eventId());
        }

        if (notification.getStatus() == NotificationStatus.SENT) {
            log.info("Duplicate event {} ignored: notification {} already sent",
                    event.eventId(), notification.getId());
            return;
        }

        try {
            sender.send(notification);
        } catch (SendFailedException e) {
            notificationService.recordFailure(notification.getId(), e.getMessage());
            throw e; // let the error handler retry / dead-letter
        }
        notificationService.markSent(notification.getId());
    }
}
