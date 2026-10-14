# Notification Service

Consumes `AppointmentBooked` events from Kafka, stores one notification per event in PostgreSQL, and "sends" it
through a stub sender that fails ~30% of the time. Failed sends are retried with back-off, then moved to a
dead-letter topic. A read-only HTTP endpoint lets you inspect the results.

Design choices and trade-offs are in [DECISIONS.md](DECISIONS.md).

## How it works

```
Kafka: appointment.booked -> AppointmentEventListener -> EventParser -> NotificationProcessor
                                                                          |-> NotificationService (Postgres)
                                                                          '-> NotificationSender (stub)
failures: retry 1s/2s/4s -> appointment.booked.dlt (status DEAD_LETTERED)
GET /notifications?status=&page=&size=
```

## Running the app

### With Docker Compose (whole stack)

Starts Postgres 16, single-node Kafka (KRaft) and the service.

```
docker compose up --build
curl localhost:8081/notifications
```

Send a test event (paste one line of JSON, then press Enter):

```
docker compose exec kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server kafka:9092 --topic appointment.booked
{"eventId":"11111111-1111-1111-1111-111111111111","eventType":"AppointmentBooked","occurredAt":"2026-11-01T08:00:00Z","appointmentId":1,"patientId":101,"doctorId":201,"startTime":"2026-11-02T10:00:00"}
```

Credentials come from the environment (copy `.env.example` to `.env`; `.env` is git-ignored).
Kafka is also reachable from the host at `localhost:29092`.

### Locally with Gradle

Requires JDK 21, a PostgreSQL database `notifications` on `localhost:5432` and Kafka on `localhost:9092`
(see `src/main/resources/application.yaml`; required env vars `DB_USERNAME` / `DB_PASSWORD`; optional `DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`).

```
./gradlew bootRun
```

The service listens on port 8081.

### Configuration

| Property | Default | Meaning |
|---|---|---|
| `notification.topic.booked` | `appointment.booked` | Topic consumed (DLT is `<topic>.dlt`) |
| `notification.sender.failure-rate` | `0.3` | Chance the stub send fails |
| `notification.retry.max-retries` | `3` | Retries before dead-lettering |
| `notification.retry.initial-interval-ms` | `1000` | First back-off, doubled each retry (max 10s) |

## Running the tests

```
./gradlew test
```

Currently the only test is `NotificationApplicationTests.contextLoads`, a `@SpringBootTest` that needs a
running PostgreSQL (`notifications` on `localhost:5432`) and Kafka (`localhost:9092`). There are no unit or
integration tests for idempotency, retry or the endpoint yet.

## What's done

- Event parsing and validation (malformed or incomplete events are rejected without retry).
- Idempotent storage keyed on `eventId` (UNIQUE constraint, race-safe).
- Stub sending, failure recording (attempts, last error), retry with exponential back-off, dead-letter topic.
- Flyway-managed schema.
- `GET /notifications` with status filter and paging (size capped at 100).
- Dockerfile and `docker-compose.yml`.

## What's not done

- No real delivery channel (email/SMS); the sender is a stub and the message is a fixed English string.
- No recipient lookup: events carry only patient and doctor IDs.
- Nothing consumes or replays the dead-letter topic.
- The HTTP endpoint has no authentication (dev use only; do not expose it). `SecurityConfiguration` permits
  `/notifications` and denies every other path.
- Almost no automated tests (only a context-load test); idempotency, retry and dead-letter behaviour are untested.
- `docker-compose.yml` has not been run (Docker was unavailable where it was written).
- The appointment-service that produces the events is not part of this repository or the compose file.

## Assumptions

- Events are JSON strings on `appointment.booked` with fields `eventId`, `eventType`, `occurredAt`,
  `appointmentId`, `patientId`, `doctorId`, `startTime`; unknown fields are ignored.
- The producer keeps `eventId` stable across its own retries.
- At-least-once delivery is acceptable. A crash between a successful send and recording it causes a repeat send.
- Retry count and intervals are reasonable defaults, not requirements.
