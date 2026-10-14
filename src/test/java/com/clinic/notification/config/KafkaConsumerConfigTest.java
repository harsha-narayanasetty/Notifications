package com.clinic.notification.config;

import com.clinic.notification.event.EventParser;
import com.clinic.notification.event.InvalidEventException;
import com.clinic.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigTest {

    private final KafkaConsumerConfig config = new KafkaConsumerConfig();

    @Mock KafkaTemplate<Object, Object> template;
    @Mock NotificationService notificationService;
    @Mock EventParser parser;

    private DefaultErrorHandler handler;

    @BeforeEach
    void setUp() {
        handler = config.kafkaErrorHandler(template, notificationService, parser, 3, 1);
    }

    @Test
    void deadLetterTopic_isNamedAfterTheMainTopicWithDltSuffix() {
        // given / when
        var topic = config.deadLetterTopic("appointment.booked");

        // then
        assertThat(topic.name()).isEqualTo("appointment.booked.dlt");
        assertThat(topic.numPartitions()).isEqualTo(1);
    }

    @Test
    void unreadableEvent_isNotRetriedAndGoesStraightToTheDeadLetterTopic() {
        // given
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("appointment.booked", 0, 5L, "1", "garbage");
        when(parser.parse("garbage")).thenThrow(new InvalidEventException("not json"));

        // when
        handler.handleOne(new InvalidEventException("not json"), record, mock(Consumer.class), mock(MessageListenerContainer.class));

        // then
        verify(template).send(any(org.apache.kafka.clients.producer.ProducerRecord.class));
    }

    @Test
    void recoveredEvent_marksStoredNotificationDeadLetteredAndPublishesToDltTopic() {
        // given
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\"}";
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("appointment.booked", 0, 9L, "7", payload);
        var event = new com.clinic.notification.event.AppointmentBookedEvent(eventId, "AppointmentBooked", null, 7L, 1L, 2L, null);
        when(parser.parse(payload)).thenReturn(event);

        // when (a non-retryable failure is recovered immediately, the same step that runs after the last retry)
        handler.handleOne(new InvalidEventException("cannot ever work"), record, mock(Consumer.class), mock(MessageListenerContainer.class));

        // then
        verify(notificationService).markDeadLettered(org.mockito.ArgumentMatchers.eq(eventId), anyString());
        verify(template).send(any(org.apache.kafka.clients.producer.ProducerRecord.class));
    }
}
