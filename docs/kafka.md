# Kafka in the Hotel Booking System

Kafka runs alongside PostgreSQL for **one job**:

1. **Event streaming** — after something important happens (e.g. a booking is created), publish an event so other parts of the system can react **asynchronously** (email, analytics, recommendations, later ES sync).

PostgreSQL remains the **source of truth**. Kafka carries **facts about what happened**, not the booking itself.

---

## Mental model

```text
Today (sync side effects — bad):
  POST /bookings → save DB → send email → update analytics → return 201
                  (user waits; email down = booking fails)

With Kafka (async side effects — good):
  POST /bookings → save DB → publish BookingCreated → return 201
                                      │
                         ┌────────────┼────────────┐
                         ▼            ▼            ▼
                   Notification  Analytics  Recommendation
                   (later)       (later)    (later)
```

| Store | Role |
|---|---|
| **PostgreSQL** | Truth (the booking row) |
| **Kafka** | Durable event log (“BookingCreated happened”) |
| **Consumers** | Side effects (email, metrics, ML) |

---

## Why not call other services synchronously?

| Approach | Problem |
|---|---|
| Call email inside `BookingService` | Booking waits for SMTP; email outage → 500 |
| Chain REST: booking → notify → analytics | Cascading failures; hard to add a 4th step |
| Do everything in one DB transaction | Couples unrelated schemas; long locks |

At Booking.com / Uber scale, one booking fans out to many downstream systems. The HTTP request must stay fast and reliable.

---

## Infrastructure

```bash
docker compose up -d kafka kafka-ui
docker compose ps   # hms_kafka healthy, hms_kafka_ui running
```

| Setting | Value | Purpose |
|---|---|---|
| Container | `hms_kafka` | Apache Kafka 3.8 (KRaft — no Zookeeper) |
| Host port | `9092` | **App on your Mac** connects here |
| Docker-internal | `kafka:9094` | **Other containers** (Kafka UI) connect here |
| Kafka UI | [http://localhost:8081](http://localhost:8081) | Browse topics, messages, consumer groups |
| Persistent volume | **none** (local) | Topics wiped if container recreated — fine for learning |

Spring Boot (`application.yml`):

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: hotel-booking-api
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
```

### Two listeners (Mac vs Docker) — why?

There is **one** Kafka broker with **two doors**:

| Who | Address | Why |
|---|---|---|
| Spring Boot on your **Mac** | `localhost:9092` | Host reaches Docker’s published port |
| Kafka UI **inside Docker** | `kafka:9094` | Container DNS; `localhost` inside UI = UI itself, not Kafka |

Same pattern as Postgres: infra in Docker, app on Mac → use `localhost:<published-port>`.

If Spring also ran in Docker Compose, it would use `kafka:9094` only.

---

## Vocabulary

| Term | Meaning | Hotel analogy |
|---|---|---|
| **Broker** | Kafka server | Post office |
| **Topic** | Named event channel | Mailbox `booking-events` |
| **Partition** | Parallel slice of a topic | Multiple slots; ordering **within** a partition |
| **Producer** | Writes messages | Booking service after create |
| **Consumer** | Reads messages | Email / analytics workers |
| **Consumer group** | Team sharing a topic — each message once per group | 3 email workers share load |
| **Offset** | Bookmark in the log | “Read up to message 42” |
| **Key** | Routes related messages to same partition | `roomId=5` events stay ordered |
| **Bootstrap servers** | Initial broker address(es) to connect | `localhost:9092` |

---

## Part 1 — Spring Boot connection (done)

### Problem

Docker Kafka exists, but the app does not know how to dial it, serialize JSON, or which topics exist.

### What “auto-config” means (important)

You will **not** see a property named `auto-config` in `application.yml`.

**Auto-configuration** is Spring Boot’s behavior: when it finds `spring-kafka` on the classpath **and** `spring.kafka.bootstrap-servers` set, it **creates beans for you** without you writing `@Bean KafkaTemplate`.

| Bean | Created by | Purpose |
|---|---|---|
| `KafkaAdmin` | Spring Boot auto-config | Talks to broker admin API; creates topics from `NewTopic` beans |
| `KafkaTemplate` | Spring Boot auto-config | Helper to **publish** messages (used in Milestone 3) |
| Consumer factories | Spring Boot auto-config | Used later by `@KafkaListener` |

You **configure** with YAML. Spring **wires** the beans. That wiring is “auto-config.”

### Why declare topic `booking-events` early?

| Reason | Explanation |
|---|---|
| **Explicit contract** | Code documents which topics the app owns |
| **Correct partitions** | We want 3 partitions now (for later parallelism), not a surprise default of 1 |
| **Verify connectivity** | If `KafkaAdmin` can create/list the topic, Spring ↔ Kafka works |
| **Not the same as publishing** | Empty topic ≠ messages. Messages come when a **producer** sends (Milestone 3) |

Creating a mailbox ≠ putting letters in it.

### Current status after Part 1

| What works | What does **not** happen yet |
|---|---|
| App connects to Kafka | `POST /api/bookings` does **not** publish |
| Topic `booking-events` exists (often empty) | No Notification / Analytics consumers |
| `KafkaTemplate` bean exists | Nobody calls `kafkaTemplate.send(...)` yet |

### Key classes (so far)

| Class | Role |
|---|---|
| `KafkaConfig` | Topic name constant + `NewTopic` bean for `booking-events` (in `booking-service`) |
| `KafkaConnectionIT` | Integration test: describe cluster + assert topic exists (in `booking-service`) |

### Verify connection

```bash
docker compose up -d kafka

cd booking-service && ./gradlew integrationTest --tests "com.hotelbooking.kafka.KafkaConnectionIT"

# Or bootRun booking-service → Kafka UI → Topics → booking-events (3 partitions)
cd booking-service && ./gradlew bootRun
# http://localhost:8081
```

CLI:

```bash
docker exec hms_kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

docker exec hms_kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe --topic booking-events
```

---

## Part 2 — Publish BookingCreated (done → upgraded to outbox)

### Problem

After a booking is saved, nothing notifies email/analytics/recommendation systems.
Those side effects must not run inside the HTTP request.

### Flow (current — transactional outbox)

```text
POST /api/bookings
        │
        ▼
Redis lock → Postgres TX:
        │     INSERT booking
        │     INSERT outbox_events (BookingCreated + HotelUpserted)
        │     COMMIT
        │
        ▼
return 201 to client   ← user does not wait for Kafka or consumers
        │
        ▼ (async, OutboxRelay every ~500ms)
Kafka publish → booking-events / hotel-events
        │  key = roomId / hotelId
```

See **Part 6 — Transactional outbox** for why this beats dual-write.

### Early learning path (dual-write — do not copy)

We first published with `KafkaTemplate` **after** commit. That teaches Kafka, but leaves a gap:

```text
Postgres commit ✅  then  Kafka publish ❌  → booking exists, no event
```

That publisher class is gone; events now go through the outbox.

### Key classes (current)

| Class | Role |
|---|---|
| `BookingCreatedEvent` | Event payload (`eventType=BookingCreated`, booking fields, `occurredAt`) |
| `OutboxService` | Serialize + insert `outbox_events` row (same TX as caller) |
| `OutboxRelay` | Poll unpublished rows → `KafkaTemplate.send` → set `published_at` |
| `BookingService` | Enqueues events **inside** the booking TX |

### Design choices

| Choice | Why |
|---|---|
| Outbox in same TX | Never emit for rolled-back bookings; never lose events after commit |
| Key = `roomId` | Ordering for the same room (inventory-style consumers) |
| Relay is async | HTTP stays fast; at-least-once delivery if Kafka was down |

### Verify

```bash
# Terminal 1 — watch topic (events appear after relay poll)
docker exec -it hms_kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic booking-events --from-beginning

# Terminal 2 — create a booking (unique future dates)
curl -s -X POST http://localhost:8080/api/bookings \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"roomId":20,"checkInDate":"2028-08-01","checkOutDate":"2028-08-03"}' | jq .

# Optional: see unpublished → published in Postgres
docker exec hms_booking_db psql -U hms_user -d hotel_booking \
  -c "SELECT outbox_id, topic, event_type, published_at IS NOT NULL AS published FROM outbox_events ORDER BY outbox_id DESC LIMIT 5;"
```

You should see JSON with `"eventType":"BookingCreated"` in Terminal 1 / Kafka UI.

```bash
cd booking-service && ./gradlew integrationTest --tests "com.hotelbooking.kafka.BookingCreatedEventIT"
```

---

## Part 3 — Consumers & consumer groups (done)

### Problem

Events sit on `booking-events` until something **reads** them. We need independent reactions:
confirmation email, analytics, recommendations — without coupling them to each other.

### Consumer groups (the core idea)

```text
                    booking-events topic
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
 notification-group   analytics-group   recommendation-group
 (Notification)       (Analytics)       (Recommendation)
```

| Setup | What happens |
|---|---|
| **Different groups** (what we built) | Each group gets **every** message → fan-out |
| **Same group, 2 instances** | Messages **split** across instances → scale workers |

One booking → three log lines (one per consumer). That is fan-out.

### Key classes

| Class | Consumer group | Simulated job |
|---|---|---|
| `NotificationBookingConsumer` | `notification-group` | Confirmation email |
| `AnalyticsBookingConsumer` | `analytics-group` | Metrics |
| `RecommendationBookingConsumer` | `recommendation-group` | Refresh suggestions |

All listen on topic `booking-events` via `@KafkaListener`.

### Verify

```bash
# Start app, then create a booking
curl -s -X POST http://localhost:8080/api/bookings \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"roomId":20,"checkInDate":"2029-04-01","checkOutDate":"2029-04-03"}' | jq .

# App logs should show all three:
# [Notification] Would send confirmation email ...
# [Analytics] Would record metric ...
# [Recommendation] Would refresh recommendations ...
```

Kafka UI → Consumer Groups: `notification-group`, `analytics-group`, `recommendation-group`.

```bash
cd notification-service && ./gradlew integrationTest --tests "com.hotelbooking.notification.kafka.NotificationBookingConsumerIT"
cd analytics-service && ./gradlew integrationTest --tests "com.hotelbooking.analytics.kafka.AnalyticsBookingConsumerIT"
cd recommendation-service && ./gradlew integrationTest --tests "com.hotelbooking.recommendation.kafka.RecommendationBookingConsumerIT"
```

### HLD note

Each group is already its **own service** (`notification-service`, `analytics-service`, `recommendation-service`). They still read the same topic — the producer in `booking-service` does not change.

---

## Part 4 — Non-blocking retry & Dead Letter Topic (done)

### Business problem

Consumers fail. Examples:

| Failure type | Example | Desired behavior |
|---|---|---|
| **Transient** | Email API timeout, DB blip | **Retry** a few times |
| **Permanent / poison** | Bad data, bug that always throws | Do **not** block the partition forever → **DLT** |

Without this: one bad message can stall a partition — every later message for that partition waits (head-of-line blocking).

### Blocking vs non-blocking (know both)

| Style | How it works | Pros | Cons |
|---|---|---|---|
| **Blocking** (`DefaultErrorHandler`) | Pause this consumer thread, retry same record | Simple | Slows that partition during retries |
| **Non-blocking** (`@RetryableTopic`) — **what we use** | Publish to `booking-events-retry-0`, `-1`, … then DLT | Main topic consumer keeps polling other messages | More topics, more moving parts |

### Flow in this project (`@RetryableTopic`)

```text
Notification listener throws (e.g. bookingRef starts with POISON-)
        │
        ▼
Spring publishes to booking-events-retry-0  (main partition unblocked)
        │  after backoff
        ▼
retry listener tries again → booking-events-retry-1 …
        │
        └── exhausted → booking-events-dlt
```

Topic names (default suffixing):

| Topic | Role |
|---|---|
| `booking-events` | Happy path |
| `booking-events-retry-0`, `-1`, … | Delayed retries |
| `booking-events-dlt` | Parking lot after attempts exhausted |

**Important:** Analytics and Recommendation use **other consumer groups** and plain listeners (no retry topics yet). A poison message that fails Notification can still succeed in Analytics/Recommendation — groups are independent.

### Key classes / config

| Piece | Role |
|---|---|
| `KafkaRetryTopicConfig` | `@EnableKafkaRetryTopic` |
| `NotificationBookingConsumer` | `@RetryableTopic` + throws on `POISON-…` |
| `HotelSearchEventConsumer` | Same pattern on `hotel-events` |
| `app.kafka.retryable.*` | `attempts`, `delay-ms` in `application.yml` |

### Verify

```bash
# Watch DLT (note: -dlt suffix, not .DLT)
docker exec -it hms_kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic booking-events-dlt --from-beginning

# Or open Kafka UI → Topics → booking-events-dlt
```

Integration test publishes a poison event directly (does not need a real HTTP booking):

```bash
cd notification-service && ./gradlew integrationTest --tests "com.hotelbooking.notification.kafka.BookingEventDltIT"
```

Normal bookings (`BK-…`) still succeed on Notification without hitting the DLT.

### Production / interview notes

| Local learning | Uber / Booking.com scale |
|---|---|
| `@RetryableTopic` non-blocking | Same idea + alerting / metrics on DLT depth |
| One DLT topic | DLT + runbooks + replay tooling |
| Simulated `POISON-` | Schema validation, idempotency, poison quarantine |

**HLD takeaway:** Retries buy resilience for blips. DLT protects **throughput** when a message is unprocessable — failure becomes visible data, not a stuck consumer.

---

## Part 5 — Ordering, partitions, and keys (done)

### Business problem

At scale you need **parallelism** (many consumers) **and** correct **ordering** for related events.
Example: for room 20, “BookingCreated A” should be processed before “BookingCreated B” if A was published first — otherwise a consumer might apply inventory updates out of order.

### Ordering rule (memorize this)

> Kafka guarantees order **only within a single partition**, not across the whole topic.

```text
Topic booking-events (3 partitions)

  P0:  [e1] [e4] [e7]     ← ordered inside P0
  P1:  [e2] [e5]           ← ordered inside P1
  P2:  [e3] [e6] [e8]     ← ordered inside P2

Global order of e1…e8 is NOT guaranteed.
```

### How the key chooses a partition

```text
partition = hash(key) % number_of_partitions
```

| Key choice | Effect |
|---|---|
| **Same key** | Always same partition → **ordered** for that key |
| **Different keys** | May land on different partitions → can be processed in parallel |
| **Null key** | Round-robin / sticky — **no** per-entity ordering |

### What we chose: `key = roomId`

```text
OutboxRelay → KafkaTemplate.send(topic, key=roomId, event)
```

| Key | Good for | Trade-off |
|---|---|---|
| **`roomId` (our choice)** | Room-level ordering (availability, room timeline) | Hot rooms → hot partitions (skew) |
| `bookingId` | Unique keys, even spread | No ordering across bookings for same room |
| `userId` | User timeline / recommendations | Celebrity users → hot partitions |

**HLD (Booking.com-style):** pick the key that matches the **consistency boundary** of the consumer. Inventory cares about `roomId`; a “user booking history” projector might prefer `userId`.

### Partitions and consumer groups (how parallelism works)

```text
3 partitions, 1 consumer in notification-group  → that one consumer reads all 3
3 partitions, 3 consumers in same group         → ideally 1 partition each (parallel)
3 partitions, 4 consumers in same group         → 4th sits idle (no partition left)
```

Rule of thumb: **partitions ≥ max parallel consumers in a group**.

### What we changed in code

| Piece | Change |
|---|---|
| Outbox `message_key` | BookingCreated key = `roomId`; HotelUpserted key = `hotelId` |
| `BookingEventPartitionKeyIT` | Asserts same `roomId` → same partition |
| Docs / earlier ITs | Expect key `"20"` for room 20 bookings |

### Verify

```bash
# Create two bookings for the same room (different dates)
# Kafka UI / partition assignment: same key → same partition

curl -s -X POST http://localhost:8080/api/bookings \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"roomId":20,"checkInDate":"2031-05-01","checkOutDate":"2031-05-03"}' | jq .

curl -s -X POST http://localhost:8080/api/bookings \
  -H 'Content-Type: application/json' \
  -d '{"userId":2,"roomId":20,"checkInDate":"2031-06-01","checkOutDate":"2031-06-03"}' | jq .
```

```bash
cd booking-service && ./gradlew integrationTest --tests "com.hotelbooking.kafka.BookingEventPartitionKeyIT"
```

### Production notes

| Local | Scale |
|---|---|
| 3 partitions | Tens / hundreds; grow with throughput |
| `roomId` key | Watch hot keys; sometimes salt key or split topics |
| Single consumer per group | Autoscale consumers up to partition count |

**HLD takeaway:** Partitions give **scale**; keys give **ordering**. You cannot have global order and unlimited parallelism on one topic at once — design which entity must stay ordered.

---

## Part 6 — Transactional outbox (done)

### Dual-write problem

```text
Bad (dual-write):
  1) COMMIT booking in Postgres
  2) Kafka.publish(BookingCreated)
     ↑ if step 2 fails, booking exists but no consumers ever run
```

### Outbox solution

```text
Good (outbox):
  1) BEGIN
       INSERT booking
       INSERT outbox_events (topic, key, type, payload JSON)
     COMMIT                 ← atomic: both or neither
  2) OutboxRelay (scheduled):
       SELECT … FOR UPDATE SKIP LOCKED
       Kafka.send(...)
       SET published_at = now()
```

| Guarantee | Meaning |
|---|---|
| **Same TX as domain write** | No event without a booking; no booking without a durable event row |
| **At-least-once to Kafka** | Relay retries until publish succeeds (consumers must be idempotent later) |
| **Decoupled HTTP** | Client does not wait for Kafka |

### Schema

`db/init/06_outbox_events.sql` — apply on existing DBs (init scripts only run on fresh volumes):

```bash
docker exec -i hms_booking_db psql -U hms_user -d hotel_booking \
  < db/init/06_outbox_events.sql
```

### Key classes

| Class | Role |
|---|---|
| `OutboxEvent` / `OutboxEventRepository` | JPA + `FOR UPDATE SKIP LOCKED` poll |
| `OutboxService` | `enqueue(topic, key, type, payload)` |
| `OutboxRelay` | `@Scheduled` publisher |
| `app.outbox.*` | `poll-interval-ms`, `batch-size` |

---

## Part 7 — Kafka-driven Elasticsearch sync (done)

### Problem

Full reindex (`POST .../reindex`) rebuilds search from scratch — fine for learning / recovery, too heavy for every booking or hotel tweak.

### Flow

```text
Booking (or admin sync)
        │
        ▼
outbox → hotel-events (key=hotelId, HotelUpserted)
        │
        ▼
HotelSearchEventConsumer (@RetryableTopic)
        │
        ▼
HotelSearchSyncService.indexHotelById → ES alias "hotels"
```

| Path | When |
|---|---|
| **Full reindex** | Nightly rebuild, mapping change, disaster recovery |
| **Kafka `HotelUpserted`** | Incremental: after booking (prices/availability projection refresh) or `POST .../hotels/{id}/sync` |

### Verify

```bash
curl -s -X POST http://localhost:8080/api/admin/search/hotels/1/sync | jq .

# Watch hotel-events
docker exec -it hms_kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic hotel-events --from-beginning
```

```bash
cd search-service && ./gradlew integrationTest --tests "com.hotelbooking.search.kafka.HotelSearchEventConsumerIT"
```

See also `docs/elasticsearch.md` (sync section).

**Deferred:** extract Notification / Analytics / Recommendation / HotelSearch consumers into real microservices — same topics, new deployables.

---

## Inspect Kafka

```bash
# List topics
docker exec hms_kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# Produce a test line (Ctrl+D to finish)
docker exec -it hms_kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic booking-events

# Consume from start
docker exec -it hms_kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic booking-events --from-beginning
```

UI: [http://localhost:8081](http://localhost:8081)

---

## Production / interview notes

| Local | Uber / Amazon / Booking.com |
|---|---|
| 1 broker | Multi-broker, multi-AZ |
| Outbox + in-process relay | Outbox + dedicated publisher workers / Debezium |
| One consumer service per group | Same, plus more partitions / lag dashboards |
| No auth | SASL/SSL, ACLs per topic |
| Topic via `NewTopic` | Terraform / topic governance |
| `@RetryableTopic` | Same + DLT dashboards / replay |

**HLD takeaway:** Kafka decouples the **write path** from **side effects**. Outbox closes the dual-write reliability gap; you still trade *immediate* side-effect consistency for reliability and scale.

---

## Build history (how we are learning it)

| Step | Status | What |
|---|---|---|
| 1 | ✅ | Docker Kafka + UI |
| 2 | ✅ | Spring config + `booking-events` topic |
| 3 | ✅ | `BookingCreated` publish path (now via outbox) |
| 4 | ✅ | Notification / Analytics / Recommendation consumers |
| 5 | ✅ | Non-blocking `@RetryableTopic` + DLT |
| 6 | ✅ | Keys, partitions, ordering deep dive |
| 7 | ✅ | Transactional outbox (`outbox_events` + relay) |
| 8 | ✅ | Kafka-driven ES sync (`hotel-events`) |
| 9 | ⏸ | Extract consumers into microservices (later) |

---
