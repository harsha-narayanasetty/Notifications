# Decisions

Scope: the **notification-service** in this repository. The producing **appointment-service** (port 8080)
is not in this repo, so I only record decisions about its side of the
contract where this service depends on them (D1, D2, D10). I did not design or review that service.

Each entry: what was decided, an alternative rejected, and what would change my mind.

## Design (found in the code)

### D1. Event contract: the service owns its own copy, tolerant reader
- **Decided:** `AppointmentBookedEvent` is a local record with `@JsonIgnoreProperties(ignoreUnknown = true)`.
  The service does not share a library or class with the producer.
- **Rejected:** a shared contract jar (couples release cycles of the two services); strict deserialization
  (any new producer field would break consumption).
- **Change my mind if:** the contract grows past a handful of events or breaks silently. Then use a schema
  registry (Avro/JSON Schema) with compatibility checks.
- **Not specified in the brief:** the payload format and the topic name. I assumed JSON strings on
  `appointment.booked` (configurable via `notification.topic.booked`).

### D2. Idempotency key is the producer's `eventId`, enforced by a UNIQUE column
- **Decided:** `notifications.event_id` is UNIQUE. `recordIfAbsent` looks up first; a racing duplicate that
  slips past the lookup hits the constraint and the processor loads the winner's row.
- **Rejected:** check-then-insert only (loses to concurrent consumers); deduplicating on `appointmentId`
  (a legitimately re-booked or rescheduled appointment would be swallowed); an in-memory seen-set (lost on
  restart, wrong across instances).
- **Change my mind if:** the producer cannot guarantee a stable `eventId` across its own retries (e.g. it
  generates a new id on republish). Then the key must be derived from business data.

### D3. Delivery semantics: at-least-once, with an idempotent consumer
- **Decided:** the Kafka offset is committed only after the record succeeds or is dead-lettered. Duplicates are
  handled by D2.
- **Rejected:** "exactly-once" via Kafka transactions (does not extend to the external send, so it would
  give a false guarantee at real cost).
- **Change my mind if:** a duplicate message to a patient becomes unacceptable. Then the real sender needs
  provider-side idempotency keys.
- **Known gap:** if the process dies after `send` succeeds but before `markSent` commits, the notification is
  sent again on redelivery. A stub cannot show this; a real provider could.

### D4. No transaction around the send
- **Decided:** `NotificationProcessor` is deliberately not `@Transactional`. It commits the PENDING row, sends
  outside any DB transaction, then records the result in a second small transaction.
- **Rejected:** one transaction around everything (holds a DB connection during a slow, failure-prone call and
  rolls back the stored row on failure, losing the attempt history).
- **Change my mind if:** sends become fast and local (e.g. an outbox write), where one transaction is simpler.

### D5. Failure handling: bounded retry, then dead-letter topic
- **Decided:** `DefaultErrorHandler` with exponential back-off (1s, 2s, 4s; 3 retries) then publish to
  `<topic>.dlt` and set status `DEAD_LETTERED`. Unparseable events (`InvalidEventException`) are not retried and
  go straight to the DLT. If the DLT publish itself fails, the record is not committed and is redelivered, so
  nothing is dropped.
- **Rejected:** infinite retry (one poison message blocks the partition); non-blocking retry topics (more
  moving parts than this volume justifies); dropping bad events with a log line (silent data loss).
- **Change my mind if:** retries cause head-of-line blocking at higher throughput. Then move to retry topics.
- **Not specified in the brief:** retry count and intervals. I picked these defaults; they are configurable.
- **Not handled:** nothing reads the DLT or replays from it. Operations would do that by hand.

### D6. Postgres + Flyway, `ddl-auto: validate`
- **Decided:** schema is owned by versioned migrations; Hibernate only validates.
- **Rejected:** `ddl-auto: update` (unreviewable schema drift in production).
- **Change my mind if:** never for production; fine for throwaway prototypes only.

### D7. Delivery is a stub behind a `NotificationSender` interface
- **Decided:** `StubNotificationSender` fails randomly (default 30%, configurable) to exercise retry and DLT.
  The channel (email/SMS) and the message template are not defined; the message is a fixed English string.
- **Rejected:** wiring a real provider (no requirements or credentials given).
- **Change my mind if:** a real channel is chosen. Then implement the interface and add recipient lookup.
- **Assumption not handled:** the event carries only patient and doctor IDs, so there is no recipient contact
  detail. A real send needs a lookup or enriched events.

### D8. Read endpoint is unauthenticated, read-only, and bounded
- **Decided:** `GET /notifications` supports a status filter and paging (size clamped to 1..100). It has no
  auth and is documented as dev-only.
- **Rejected:** no endpoint (harder to inspect results); a full authenticated API (not asked for).
- **Change my mind if:** it must exist outside development. Then add authentication and authorization.
  `spring-boot-starter-security` is on the classpath but not configured by me.
- **Not handled:** the notification message contains patient/doctor IDs. If this is sensitive data, the
  endpoint must not be exposed as is.

## Testing and packaging (my decisions in this session)

### D9. Unit tests are database-free; integration tests use embedded Postgres
- **Decided:** unit tests use Mockito or an in-memory fake. Integration tests use `zonky` embedded PostgreSQL
  so real Flyway and the real UNIQUE constraint are exercised.
- **Rejected:** H2 (does not behave like Postgres for constraints and types); Testcontainers (requires
  Docker for every developer and CI runner).
- **Change my mind if:** tests depend on Postgres features the embedded binary lacks, or Kafka behaviour
  itself needs verification. Then use Testcontainers.
- **Not covered:** the real Kafka retry, DLT and listener wiring are not tested end to end. `KafkaConsumerConfig`
  and the listener are tested in isolation only.

### D10. Docker Compose runs the full stack (Postgres, single-node KRaft Kafka, the service)
- **Decided:** one `docker-compose.yml`, healthchecks gating startup, configuration through Spring
  environment overrides, and the existing multi-stage `Dockerfile`.
- **Rejected:** Zookeeper-based Kafka (extra service, obsolete); baking config into the image.
- **Change my mind if:** this must resemble production. Then use multiple brokers, replication factor above 1,
  secrets management and an external DB.
- **Not verified:** Docker is not installed in my environment, so the compose file has not been run.
  The appointment-service is not part of it because its image and source were not available to me.
- **Known weaknesses:** the default DB password is `password`, and the Kafka listener on the host port is
  plaintext.
