package com.clinic.notification.event;

/** The message is unreadable or incomplete. Retrying can never fix it, so it goes straight to the dead-letter topic. */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
