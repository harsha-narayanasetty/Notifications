package com.clinic.notification.config;

import com.clinic.notification.event.EventParser;
import com.clinic.notification.event.InvalidEventException;
import com.clinic.notification.sender.PermanentSendException;
import com.clinic.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public NewTopic deadLetterTopic(@Value("${notification.topic.booked:appointment.booked}") String topic) {
        return TopicBuilder.name(topic + ".dlt").partitions(1).replicas(1).build();
    }

    /**
     * Retry policy: the first attempt plus maxRetries more (1s, 2s, 4s by default), then the record is
     * published to <topic>.dlt. Unreadable events skip the retries and go straight to the DLT.
     * Permanent send failures (PermanentSendException) also skip the retries.
     * If publishing to the DLT fails temporarily, the record is NOT committed and is delivered again,
     * so an event is never dropped. If it fails permanently (record too large, bad topic), retrying would
     * block the partition forever, so the full record is logged at ERROR and the offset moves on.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template,
                                                 NotificationService notificationService,
                                                 EventParser parser,
                                                 @Value("${notification.retry.max-retries:3}") int maxRetries,
                                                 @Value("${notification.retry.initial-interval-ms:1000}") long initialIntervalMs) {

        DeadLetterPublishingRecoverer deadLetterPublisher = new DeadLetterPublishingRecoverer(
                template, (record, ex) -> new TopicPartition(record.topic() + ".dlt", -1));

        // Forces the consumer to wait for the broker to acknowledge the DLT write.
        // If the broker times out or fails, this throws an exception and stops the offset commit.
        deadLetterPublisher.setFailIfSendResultIsError(true);

        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            String reason = String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
            try {
                var event = parser.parse((String) record.value());
                notificationService.markDeadLettered(event.eventId(), reason);
            } catch (Exception ignored) {
                // unreadable event: nothing stored to update, it still goes to the DLT below
            }
            log.error("Giving up on record {}-{}@{}: {} -> dead-letter topic",
                    record.topic(), record.partition(), record.offset(), reason);
            try {
                deadLetterPublisher.accept(record, ex);
            } catch (RuntimeException publishFailure) {
                if (!isPermanentPublishFailure(publishFailure)) {
                    throw publishFailure;
                }
                log.error("Dead-letter publish can never succeed ({}). Skipping the record so the partition is not "
                                + "blocked. Record to recover by hand: {}-{}@{} key={} value={}",
                        publishFailure.toString(), record.topic(), record.partition(), record.offset(),
                        record.key(), record.value());
            }
        };

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class, PermanentSendException.class);
        return handler;
    }

    static boolean isPermanentPublishFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof RecordTooLargeException
                    || t instanceof SerializationException
                    || t instanceof InvalidTopicException) {
                return true;
            }
        }
        return false;
    }
}
