package com.clinic.notification.model;

public enum NotificationStatus {
    PENDING,        // stored, not yet delivered (still being retried)
    SENT,           // the (stub) send succeeded
    DEAD_LETTERED   // retries exhausted; the event was moved to the dead-letter topic
}
