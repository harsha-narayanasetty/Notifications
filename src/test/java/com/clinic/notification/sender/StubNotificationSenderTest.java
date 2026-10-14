package com.clinic.notification.sender;

import com.clinic.notification.model.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.clinic.notification.testutil.TestData.EVENT_ID;
import static com.clinic.notification.testutil.TestData.notification;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubNotificationSenderTest {

    @Test
    @DisplayName("Given failure rate 0, when send, then it never fails")
    void send_zeroFailureRate_neverFails() {
        // given
        StubNotificationSender sender = new StubNotificationSender(0.0);

        // when / then
        for (int i = 0; i < 200; i++) {
            assertThatCode(() -> sender.send(notification(1L, NotificationStatus.PENDING)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Given failure rate 1, when send, then it always throws SendFailedException mentioning the event id")
    void send_fullFailureRate_alwaysFails() {
        // given
        StubNotificationSender sender = new StubNotificationSender(1.0);

        // when / then
        for (int i = 0; i < 50; i++) {
            assertThatThrownBy(() -> sender.send(notification(1L, NotificationStatus.PENDING)))
                    .isInstanceOf(SendFailedException.class)
                    .hasMessageContaining(EVENT_ID.toString());
        }
    }
}
