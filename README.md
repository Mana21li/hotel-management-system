# Hotel Management System

Learning project for hotel bookings: Java 25, Spring Boot 3.5, PostgreSQL, Redis, Elasticsearch, Kafka.

Public client URLs stay on **`:8080`**. The `backend/` directory is no longer a monolith — it is a **strangler/gateway** that forwards:

| Path | Downstream |
|---|---|
| `GET /api/hotels` | hotel-service `:8086` |
| `GET /api/search/*`, admin reindex/sync | search-service `:8083` |
| `POST /api/bookings` | booking-service `:8087` (lock + EXCLUDE + outbox) |

Notification (`:8082`), analytics (`:8084`), and recommendation (`:8085`) consume Kafka only (`GET /internal/stats` for ops).

Shared Postgres is gone — **two containers**: `hotel-db` (`hotel_catalog`) and `booking-db` (`hotel_booking`). Search reads catalog via hotel-service REST, not JDBC.

## Run locally

```bash
cp .env.example .env
docker compose down -v   # required once after DB split
docker compose up -d     # hotel-db :5433, booking-db :5434, redis, es, kafka
docker compose --profile app up -d --build
```

Boot from host: set `HOTEL_POSTGRES_PORT=5433`, `BOOKING_POSTGRES_PORT=5434` (defaults in `application.yml`).

## Layout

| Directory | Role |
|---|---|
| `backend/` | Public HTTP edge on `:8080` (controllers + RestClient gateways) |
| `hotel-service/` | Catalog reads + Redis hotel cache |
| `search-service/` | Elasticsearch queries + `HotelUpserted` consumer |
| `booking-service/` | Booking TX, Redis lock, outbox → Kafka |
| `notification-service/` / `analytics-service/` / `recommendation-service/` | Kafka consumers |
| `docs/` | Architecture notes (`docs/stage4-microservices.md`) |

Architecture, Kafka, Redis, and ES: `docs/stage4-microservices.md`, `docs/kafka.md`, `docs/redis.md`, `docs/elasticsearch.md`.
