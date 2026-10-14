package com.clinic.notification.integration;

import com.clinic.notification.model.Notification;
import com.clinic.notification.repository.NotificationRepository;
import com.clinic.notification.sender.NotificationSender;
import com.clinic.notification.sender.SendFailedException;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the real application (real Flyway migration, real database constraints) against a throw-away embedded
 * PostgreSQL started by the test itself, so no Docker or local database is needed. Kafka points at an unused port
 * and the listener is not started; events are fed to the processor directly, exactly as the Kafka listener does.
 * The random-failure stub sender is replaced by a controllable one.
 */
@SpringBootTest
@Import(IntegrationTestBase.TestSenderConfig.class)
abstract class IntegrationTestBase {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignored) {
                    // JVM is exiting
                }
            }));
            return pg;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start embedded PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.kafka.admin.fail-fast", () -> "false");
        registry.add("spring.kafka.admin.properties.default.api.timeout.ms", () -> "1000");
        registry.add("spring.kafka.admin.properties.request.timeout.ms", () -> "1000");
    }

    /** Counts sends and can be told to fail the next N attempts. */
    static class ControllableSender implements NotificationSender {
        final List<UUID> sentEventIds = new CopyOnWriteArrayList<>();
        final AtomicInteger failuresToSimulate = new AtomicInteger();

        @Override
        public void send(Notification notification) {
            if (failuresToSimulate.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new SendFailedException("simulated failure");
            }
            sentEventIds.add(notification.getEventId());
        }
    }

    @TestConfiguration
    static class TestSenderConfig {
        @Bean
        @Primary
        ControllableSender controllableSender() {
            return new ControllableSender();
        }
    }

    @Autowired protected NotificationRepository repository;
    @Autowired protected ControllableSender sender;
    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE notifications RESTART IDENTITY");
        sender.sentEventIds.clear();
        sender.failuresToSimulate.set(0);
    }
}
