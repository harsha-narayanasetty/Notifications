package com.clinic.notification.sender;

/** The send can never succeed (e.g. invalid recipient). Not retried: the event is dead-lettered immediately. */
public class PermanentSendException extends SendFailedException {
    public PermanentSendException(String message) {
        super(message);
    }
}
