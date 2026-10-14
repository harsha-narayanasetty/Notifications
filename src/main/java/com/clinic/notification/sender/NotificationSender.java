package com.clinic.notification.sender;

import com.clinic.notification.model.Notification;

public interface NotificationSender {

    /** @throws SendFailedException if delivery failed (the caller will retry) */
    void send(Notification notification);
}
