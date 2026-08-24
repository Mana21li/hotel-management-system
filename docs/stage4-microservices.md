# Stage 4 — Monolith to Microservices (Learnings)

This doc captures **what I learned**, not implementation steps. Read it to refresh the
mental models behind the migration. Status: Milestones 1–6 done. Public traffic still
enters `:8080` (strangler); Notification, Search, Analytics, Recommendation, Hotel,
and Booking run as separate processes.

---

## The one-sentence summary

> Split a monolith along **business capability + data ownership + failure domain** lines,
> keep client contracts stable behind a gateway, communicate async by default, and
> extract the lowest-risk service first.

---

## Milestone 1 — Service boundaries

### Why boundaries exist

Not because "microservices are better." Boundaries align three things:

| Axis | Question it answers |
|---|---|
| **Team ownership** | Who is on-call for search relevance vs booking correctness? |
| **Data ownership** | Which service is allowed to write `bookings`? |
| **Failure isolation** | Should an email outage take down booking creation? |

If splitting doesn't improve one of these, don't split.

### The boundaries in this project

| Service | Owns | Key insight |
|---|---|---|
| **Hotel** | Catalog truth (`hotels`), hotel cache | Upstream identity — others depend on it, never redefine it |
| **Booking** | Write path: locks, EXCLUDE constraint, outbox | Highest correctness bar → highest extraction risk → extract **late** |
| **Search** | ES index, sync, search API | A CQRS read model, not a database feature |
| **Notification** | Reacting to `BookingCreated` | No DB writes, stable event input → **safest first extraction** |

Analytics / Recommendation are siblings of Notification (same pattern, same safety).

### Bad splits (know why they are wrong)

| Anti-pattern | Why it fails |
|---|---|
| One service per controller | Network chatter, no business cohesion |
| "Lock service" for Redis locks | A lock is a *mechanism* of Booking, not a domain |
| Split by technical layer (controller-svc, repo-svc) | Every request crosses every service |

### Interview honesty

Distinguish **implemented** (BookingCreated, HotelUpserted, hotel cache, booking locks)
from **schema-only** (payments, reviews tables). Claiming unbuilt features destroys
credibility faster than a smaller scope does.

---

## Milestone 2 — Target architecture design

### API Gateway

Single stable entry point so clients never learn service addresses.

- Does: routing, authN (validate JWT once), rate limiting, TLS.
- Never does: business logic or DB access — a "smart gateway" is a second monolith.
- **Migration superpower:** move one route at a time from monolith to a new service;
  clients never notice. The gateway makes gradual extraction possible.

### REST vs Kafka — the decision rule

> **Does the caller need the answer to finish its own request?**
> Yes → REST (sync). No → Kafka (async).

| Interaction | Choice | Why |
|---|---|---|
| Client → create booking | REST | User needs 201/409 now |
| Booking → Notification/Analytics | Kafka | Side effects; independent failure |
| Hotel/Booking → Search refresh | Kafka (`HotelUpserted`) | Read-model projection |
| Search → "is room free?" at query time | Neither | Search stays approximate; write side revalidates at booking time |

Two rules:

1. Never chain sync calls for side effects (Booking must not REST-call Notification).
2. Every sync call needs a timeout + fallback (degrade, don't fail the booking).

### Database ownership

> **Database-per-service really means: one writing service per table.**
> Physical DB separation comes later; ownership discipline comes first.

Migration order: logical ownership (only Booking code touches `bookings`) →
separate schemas → separate databases. Never big-bang.

### The "pragmatic split" (rooms — the contested table)

Both Hotel ("rooms describe my property") and Booking ("rooms are inventory I lock")
have legitimate claims. The pure answers both hurt:

- Hotel owns all of rooms → Booking must call Hotel on **every** booking (sync dependency in the critical path).
- Booking owns all of rooms → Hotel can't manage its own catalog.

**Resolution: split the concept, not the table.**

| Concern | Owner |
|---|---|
| Room *definition* (number, type, base price) | Hotel |
| Room *availability* (booking rows, EXCLUDE, locks) | Booking |

Where Booking needs definition data, it **snapshots at write time** — the booking row
copies `pricePerNight` at creation. Not just an architecture trick: the customer keeps
the agreed price even if the hotel raises prices tomorrow. Amazon orders snapshot
product price; Uber trips snapshot the fare quote. Same pattern.

### Shared libraries

> **Share contracts, not code.** A shared `common-lib` with entities and business
> helpers is a distributed monolith with extra steps.

| Share | Never share |
|---|---|
| Event contracts (small versioned jar) | JPA entities |
| Thin logging/tracing config | Repositories, services, business rules |

Event contracts evolve **additively only** (add optional fields; never rename/remove —
old messages still sit on the topic). Schema Registry + Avro is the industrial version
of this discipline.

### DTO strategy — three shapes, three audiences

| Shape | Contract with | Versions with |
|---|---|---|
| API DTO (`BookingResponse`) | REST clients | the endpoint |
| Domain entity (`Booking`, JPA) | nobody — internal | the schema (free to change) |
| Event payload (`BookingCreatedEvent`) | Kafka consumers | the topic |

Entities never cross a process boundary. The mapping boilerplate is the **price of
independent evolution** — a feature, not a smell.

---

## CQRS (named during this stage)

The search stack built earlier *is* CQRS:

```text
Command side (correct):  Postgres, normalized, EXCLUDE constraint, transactions
        │
        └── outbox → Kafka (HotelUpserted) → projection
                                              │
Query side (fast):       Elasticsearch, denormalized docs, ranked + fuzzy
```

- The JOIN + MIN(price) happens **once at write time** (projection), not on every query.
- Cost: eventual consistency (~sub-second staleness). Acceptable for search; never for
  double-booking — which is why availability is enforced on the **write side**.
- CQRS ≠ event sourcing. Postgres rows are truth; events are notifications.

---

## Milestone 3 — Containerizing the monolith

### Why containerize before splitting

You cannot run five services as `./gradlew bootRun` in five terminals. Containers make
the app a **deployable artifact**: same image locally, in CI, in production. This is
the prerequisite for CI/CD (M4) and extraction (M5–6) — not an optimization.

### Multi-stage builds

| Stage | Contains | Why separate |
|---|---|---|
| Build | JDK + Gradle + source | Needed to compile, ~1GB+ |
| Runtime | JRE + fat jar only | Smaller image, smaller attack surface, faster pulls |

Layer-caching trick: copy build files and download dependencies **before** copying
source — source changes then rebuild in seconds, not minutes.

### Container networking (the lesson that bites everyone)

Inside the compose network, `localhost` means *the container itself*. Services reach
each other by **service name on the container port**:

| From the Mac | From inside a container |
|---|---|
| `localhost:5432` | `db:5432` |
| `localhost:6379` | `redis:6379` |
| `localhost:9200` | `elasticsearch:9200` |
| `localhost:9092` | `kafka:9094` — the *other* advertised listener |

The Kafka two-listener setup (from `docs/kafka.md`) exists precisely for this moment:
the containerized app is the first real user of `kafka:9094`.

### 12-factor config

The app image is **environment-agnostic**: hosts come from env vars with localhost
defaults. Same image runs on the Mac workflow, in compose, or in production — only
the environment changes. Never bake hostnames into the artifact.

### Other habits worth naming

- **Non-root user** in the runtime image (container escape hygiene).
- **Healthchecks + `depends_on: service_healthy`** — orchestration by readiness,
  not by start order.
- **Compose profiles** — the app container is opt-in, so the old dev workflow
  (`bootRun` on the Mac) still works. Backward compatibility applies to dev
  workflows too, not just APIs.

---

## Milestone 4 — CI/CD (build & verify, no deploy)

### Why CI before extracting services

Every future service will need the same gates. Building the pipeline on the
**monolith first** means extraction inherits a quality bar instead of inventing
one under pressure. CI answers: “Can a stranger (or tomorrow-you) trust this
commit without clicking around manually?”

### Pipeline shape (what each gate protects)

```text
push / PR
   │
   ▼
Unit tests          → logic regressions (fast, no Docker)
   │
   ▼
Compose infra up    → same Postgres/Redis/ES/Kafka as local
   │
   ▼
Integration tests   → locks, outbox, Kafka, ES sync still work
   │
   ▼
Jacoco + SonarQube CE → coverage + static quality (self-hosted, free)
   │
   ▼
bootJar             → the runnable artifact exists
   │
   ▼
docker build        → the container image builds (not pushed — no deploy yet)
```

### Unit vs integration — a deliberate split

| Task | Matches | Needs Docker? |
|---|---|---|
| `./gradlew test` | `*Test` (mocked) | No |
| `./gradlew integrationTest` | `*IT` + context smoke | Yes |

CI starts compose before ITs rather than rewriting tests to Testcontainers —
preserves working tests and matches the local mental model. Testcontainers is a
valid later upgrade when you want per-test isolation without a shared compose stack.

### SonarQube Community Edition (self-hosted) vs SonarCloud

| | SonarCloud | SonarQube CE (what we use) |
|---|---|---|
| Cost | Free tier for public repos | Free, self-hosted |
| Where it runs | SaaS | Docker (`--profile sonar`) |
| Auth | GitHub secret `SONAR_TOKEN` | CI bootstraps a token on a fresh instance |
| Data | Leaves your machine | Stays in local/CI volumes |

Local UI: `docker compose --profile sonar up -d` → http://localhost:9000

Sonar flags smells, bugs, and coverage gaps. It does **not** authorize drive-by
refactors of production-stable code. Treat findings as a backlog you triage.

Own Postgres for Sonar (`sonar_db`) — never share the hotel booking database.
Quality tooling and product data stay in separate failure domains.

### Build ≠ deploy

Milestone 4 stops at **artifact creation**. Deploying (pushing an image,
rolling out to an environment) is a separate decision with rollback, secrets,
and environment config. Companies separate “CI” (verify) from “CD” (release)
for exactly this reason — a green build does not automatically mean production.

### Industry anchors

GitHub Actions ≈ Jenkins/GitLab CI at learning scale. Same ideas: immutable
commit SHA as image tag, fail-fast on unit tests before expensive IT/infra,
quality gates before merge, promote the same image later (not rebuild per env).

---

## Tradeoffs of splitting (say these unprompted)

| Benefit | Cost |
|---|---|
| Independent deploy/scale | N pipelines, N health checks, gateway HA |
| Failure isolation | Timeouts, retries, circuit breakers everywhere |
| Clear ownership, team parallelism | No cross-service ACID — outbox/sagas |
| Stable client contract via gateway | Cross-service debugging is harder |

**Industry anchors:** Netflix (gateway + client-side resilience), Amazon (service-owned
data, API-only access), Uber/Booking.com (outbox/CDC → Kafka → owned read models).
This project is the same architecture at learning scale.

---

## Milestone 5 — Extract Notification Service (done)

### Why Notification first

- Consumes stable Kafka input (`BookingCreated`) — no new REST coupling to Booking
- No Postgres writes today (log stub)
- Booking path never waits on it — lowest blast radius

### What moved

```text
Before:  one process produces + consumes notification
After:   booking-service produces (outbox) → Kafka → notification-service consumes
```

| Stays with Booking (now `booking-service`) | Moves to notification-service |
|---|---|
| Booking APIs, outbox, `BookingCreated` publish | `NotificationBookingConsumer`, `@RetryableTopic`, DLT handling |
| — | `GET /internal/stats` (test visibility) |

Event JSON contract unchanged — same `BookingCreatedEvent` field names/types.

### How to verify locally

```bash
docker compose up -d
cd notification-service && ./gradlew bootRun &
cd backend && ./gradlew bootRun

# E2E
cd backend && ./gradlew e2eTest

# Or full stack in Docker
docker compose --profile app up -d --build
curl -s -X POST http://localhost:8080/api/bookings -H 'Content-Type: application/json' \
  -d '{"userId":1,"roomId":20,"checkInDate":"2036-01-01","checkOutDate":"2036-01-03"}'
curl -s http://localhost:8082/internal/stats
```

### Test layers

| Layer | Where | Proves |
|---|---|---|
| Unit | `NotificationStatsControllerTest` | HTTP stats endpoint |
| Service IT | `NotificationBookingConsumerIT`, `BookingEventDltIT` | Kafka consume + retry/DLT |
| Cross-service E2E | `BookingNotificationE2EIT` (`backend/` strangler) | Booking → outbox → Kafka → notification |

---

## Milestone 6 — Extract Search Service (done)

### Why Search next

Search is already a CQRS read model: Postgres writes elsewhere, Elasticsearch +
`HotelUpserted` live here. Extracting it isolates search failures from booking.

Public paths stay on `:8080` via a **strangler proxy** so clients do not change
before an API Gateway exists.

```text
Client → strangler :8080 /api/search/*  →  search-service :8083 (ES)
Booking TX → outbox HotelUpserted → Kafka hotel-events → search-service consumer → ES
```

| Stays on `:8080` / booking / hotel | Moves to search-service |
|---|---|
| Public search paths on `:8080` | ES queries, batch reindex, incremental `HotelUpserted` consumer |
| Public paths `GET /api/search/hotels`, admin reindex/sync | Real implementations + `GET /internal/stats` |
| Redis locks stay in `booking-service` | Catalog REST → hotel-service for ES reindex (no JDBC in search-service) |

### Design choices worth defending

| Choice | Why |
|---|---|
| Strangler proxy on `:8080` | Keep client URLs stable; a real API Gateway comes later |
| **One** consumer group `hotel-search-group` | Same group ≠ fan-out. Only search-service may join it |
| `spring.json.use.type.headers: false` + default type | Producer and consumer Java packages differ |
| Admin `.../sync` publishes Kafka **directly** | Not a dual-write with a domain TX; outbox stays with Booking |
| Catalog REST (not shared JDBC) | search-service has no datasource; hotel-service owns `hotel_catalog` |

### How to verify locally

```bash
docker compose up -d
# create hotels_v1 + alias if missing (see docs/elasticsearch.md)

cd search-service && ./gradlew test integrationTest
cd search-service && ./gradlew bootRun &     # :8083
cd notification-service && ./gradlew bootRun &  # :8082, needed for *E2EIT*
cd backend && ./gradlew test integrationTest e2eTest

# Must not run a stale :8080 process that still joins hotel-search-group.
```

### Test layers

| Layer | Where | Proves |
|---|---|---|
| Unit | `SearchStatsControllerTest` | HTTP stats endpoint |
| Service IT | `HotelSearchIT`, `HotelSearchSyncIT`, `HotelSearchEventConsumerIT` | ES query, reindex, Kafka → ES |
| Cross-service E2E | `SearchApiProxyE2EIT` (`backend/` strangler) | Client path → proxy → search-service |

Remaining in Milestone 6: none — Analytics, Recommendation, Hotel, and Booking are extracted.
Public APIs still enter via the `:8080` strangler.

### The rest of Milestone 6 (done in the same pass)

| Service | Port | What moved | Public contract |
|---|---|---|---|
| Analytics | 8084 | `analytics-group` consumer + unique retry/DLT topics | No product REST; `GET /internal/stats` |
| Recommendation | 8085 | `recommendation-group` consumer + unique retry/DLT topics | Same |
| Hotel | 8086 | `GET /api/hotels`, Redis hotel cache | Paths stay on `:8080` via strangler |
| Booking | 8087 | Lock + EXCLUDE + outbox producer | `POST /api/bookings` stays on `:8080` |

```text
Client → :8080  ┬─ /api/search/*     → search-service :8083
                ├─ /api/hotels       → hotel-service :8086
                └─ POST /api/bookings → booking-service :8087
                                          └─ outbox → Kafka → notification / analytics / recommendation / search
```

Booking was last because the lock, the `EXCLUDE` constraint, and the outbox relay must move as one unit. `:8080` is now a strangler/gateway (RestClient proxies only) — no Postgres, Redis, or Kafka in that process.

Fan-out proof: `BookingFanoutE2EIT` creates one booking and waits for all three consumer services to increment `processedCount`.

---

## Milestone 7 — Database-per-service (done)

Process split (M6) left **one shared Postgres**. M7 completes logical + physical separation:

| Database | Owner | Tables |
|---|---|---|
| `hotel_catalog` | hotel-service | `countries`, `cities`, `room_types`, `hotels`, `rooms` |
| `hotel_booking` | booking-service | `bookings`, `payments`, `reviews`, `outbox_events` |
| `hotel_user` | user-service | `users` |

Cross-DB FKs are gone. `bookings.user_id`, `bookings.room_id`, and `reviews.user_id` are logical references; overlap prevention stays on `hotel_booking.bookings` via `EXCLUDE`.

**Sync reads across boundaries:**

| Caller | Needs | How |
|---|---|---|
| booking-service | room price + hotel name at write time | REST → hotel-service `/internal/catalog/*` |
| booking-service | active user at write time | REST → user-service `/internal/users/{id}` |
| search-service | denormalized hotel rows for ES | REST → `/internal/catalog/hotels/search-projections` |

Still **one Postgres server process per service** locally (`hotel-db`, `booking-db`, `user-db` containers). Production would use separate managed instances — same ownership rules.

**Next: API Gateway milestone:** JWT, rate limiting, Spring Cloud Gateway.

### Verify

```bash
docker compose down -v && docker compose up -d
docker exec hms_hotel_db psql -U hms_user -d hotel_catalog -c '\dt'
docker exec hms_booking_db psql -U hms_user -d hotel_booking -c '\dt'

cd hotel-service && ./gradlew bootRun &
cd search-service && ./gradlew bootRun &
cd booking-service && ./gradlew bootRun &
cd backend && ./gradlew e2eTest
```

---

## Extraction order

1. **Notification** — done. Stable event input, no DB writes, lowest blast radius.
2. **Search** — done. Read model, ES + admin APIs, strangler proxy.
3. **Analytics / Recommendation** — done. Same Kafka consumer pattern as Notification, unique retry/DLT suffixes.
4. **Hotel** — done. Catalog reads + Redis cache; Booking snapshots hotel name via catalog REST.
5. **Booking last** — done. Lock + EXCLUDE + outbox moved together; `:8080` is now a strangler/gateway only.

> Rule: extract the lowest-risk consumer first; never start by exploding the
> transactional core.

---

## Milestone log

| Milestone | Status | Learning theme |
|---|---|---|
| 1 — Boundary analysis | ✅ | Boundaries = ownership + failure, not package count |
| 2 — Target architecture | ✅ | Gateway, sync/async rule, one-writer-per-table, contracts not code, 3 DTO shapes |
| 3 — Containerize monolith | ✅ | Multi-stage builds, container networking, 12-factor config, health-based orchestration |
| 4 — CI/CD | ✅ | Unit/IT split, compose-in-CI, SonarQube CE (self-hosted), build ≠ deploy |
| 5 — Extract Notification Service | ✅ | First microservice; Kafka-only; E2E test across services |
| 6 — Extract remaining | ✅ | Search, Analytics, Recommendation, Hotel, Booking; :8080 is the strangler |
| 7 — Database-per-service | ✅ | `hotel_catalog` + `hotel_booking` + `hotel_user`; catalog/user REST for cross-boundary reads |
| 8 — User service + Sonar on extracted services | ✅ | user-service `:8088`; Jacoco + Sonar per module in CI |

---

## Related docs

- Kafka + outbox: `docs/kafka.md`
- Elasticsearch read model: `docs/elasticsearch.md`
- Redis cache + locks: `docs/redis.md`
- Schema: `docs/database-schema-postgres.md`
