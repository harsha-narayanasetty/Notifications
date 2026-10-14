package com.clinic.notification.sender;

public class SendFailedException extends RuntimeException {
    public SendFailedException(String message) {
        super(message);
    }
}
