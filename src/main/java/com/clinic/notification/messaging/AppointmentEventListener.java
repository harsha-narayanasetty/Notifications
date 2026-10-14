package com.clinic.notification.messaging;

import com.clinic.notification.event.EventParser;
import com.clinic.notification.service.NotificationProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppointmentEventListener {

    private final EventParser parser;
    private final NotificationProcessor processor;

    /**
     * Any exception thrown here is handled by the DefaultErrorHandler in KafkaConsumerConfig:
     * bounded retries with back-off, then the dead-letter topic. The offset is only committed
     * once the record has succeeded or been dead-lettered, so no event is silently lost.
     */
    @KafkaListener(topics = "${notification.topic.booked:appointment.booked}")
    public void onAppointmentBooked(String payload) {
        processor.process(parser.parse(payload));
    }
}
