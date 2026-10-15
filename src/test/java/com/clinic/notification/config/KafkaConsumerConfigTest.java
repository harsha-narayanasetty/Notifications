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
import static org.mockito.Mockito.never;
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

    @Test
    void permanentSendFailure_goesStraightToTheDeadLetterTopicWithoutRetries() {
        // given
        var record = new ConsumerRecord<Object, Object>("appointment.booked", 0, 1L, "7", "{}");
        when(parser.parse("{}")).thenThrow(new InvalidEventException("x"));

        // when
        boolean handled = handler.handleOne(new com.clinic.notification.sender.PermanentSendException("bad number"),
                record, mock(Consumer.class), mock(MessageListenerContainer.class));

        // then
        assertThat(handled).isTrue();
        verify(template).send(any(org.apache.kafka.clients.producer.ProducerRecord.class));
    }

    @Test
    void ordinaryFailure_isRetriedBeforeBeingDeadLettered() {
        // given
        var record = new ConsumerRecord<Object, Object>("appointment.booked", 0, 1L, "7", "{}");

        // when
        boolean firstAttempt = handler.handleOne(new IllegalStateException("boom"), record,
                mock(Consumer.class), mock(MessageListenerContainer.class));

        // then
        assertThat(firstAttempt).as("first failure is retried, not recovered").isFalse();
        verify(template, never()).send(any(org.apache.kafka.clients.producer.ProducerRecord.class));
    }

    @Test
    void permanentDeadLetterPublishFailure_isSkippedSoThePartitionIsNotBlocked() {
        // given
        var record = new ConsumerRecord<Object, Object>("appointment.booked", 0, 1L, "7", "garbage");
        when(parser.parse("garbage")).thenThrow(new InvalidEventException("x"));
        when(template.send(any(org.apache.kafka.clients.producer.ProducerRecord.class))).thenReturn(
                java.util.concurrent.CompletableFuture.failedFuture(
                        new org.apache.kafka.common.errors.RecordTooLargeException("too big")));

        // when
        boolean handled = handler.handleOne(new InvalidEventException("x"), record,
                mock(Consumer.class), mock(MessageListenerContainer.class));

        // then
        assertThat(handled).isTrue();
    }

    @Test
    void temporaryDeadLetterPublishFailure_isRedeliveredNotSkipped() {
        // given
        var record = new ConsumerRecord<Object, Object>("appointment.booked", 0, 1L, "7", "garbage");
        when(parser.parse("garbage")).thenThrow(new InvalidEventException("x"));
        when(template.send(any(org.apache.kafka.clients.producer.ProducerRecord.class))).thenReturn(
                java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        // when
        boolean handled = handler.handleOne(new InvalidEventException("x"), record,
                mock(Consumer.class), mock(MessageListenerContainer.class));

        // then
        assertThat(handled).isFalse();
    }

    @Test
    void permanentPublishFailure_isDetectedThroughWrappedCauses() {
        assertThat(KafkaConsumerConfig.isPermanentPublishFailure(new RuntimeException(
                new org.apache.kafka.common.errors.RecordTooLargeException("big")))).isTrue();
        assertThat(KafkaConsumerConfig.isPermanentPublishFailure(new IllegalStateException("down"))).isFalse();
    }
}
