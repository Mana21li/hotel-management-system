# Elasticsearch in the Hotel Booking System

Elasticsearch runs alongside PostgreSQL for **one job**:

1. **Hotel discovery / search** — full-text query, filters, sort, pagination, typo tolerance (`GET /api/search/hotels`)

PostgreSQL remains the **source of truth**. Elasticsearch is a **search read model** (a denormalized copy optimized for search). If ES is empty or stale, rebuild it from Postgres.

---

## Mental model

| Store | Role |
|---|---|
| **PostgreSQL** | Transactions, bookings, normalized hotel/city/room data |
| **Redis** | Cache + distributed locks |
| **Elasticsearch** | Search / discovery only |

```text
Write path (truth):     Client → Spring → Postgres
Search path (discovery): Client → Spring → Elasticsearch
Sync path:              Postgres → (reindex) → Elasticsearch
```

Search never hits Postgres. Booking never depends on Elasticsearch.

---

## Infrastructure

```bash
docker compose up -d elasticsearch kibana
docker compose ps   # hms_elasticsearch healthy, hms_kibana running
```

| Setting | Value | Purpose |
|---|---|---|
| Container | `hms_elasticsearch` | Search engine |
| Image | `elasticsearch:8.15.3` | Pinned |
| Host port | `9200` | App + curl connect here |
| `discovery.type` | `single-node` | Local cluster of one |
| Security | disabled locally | Prod must enable TLS/auth |
| Heap | `512m` | Cap RAM |
| Volume | `hms_esdata` | Persist index data |
| Kibana | `http://localhost:5601` | Optional UI / Dev Tools |

Spring Boot:

```yaml
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URI:http://localhost:9200}

search:
  elasticsearch:
    hotels-index-alias: hotels
    fuzzy-enabled: true
    max-query-length: 200
```

### Verify cluster

```bash
curl -s 'http://localhost:9200/_cluster/health?pretty'
curl -s 'http://localhost:9200/_cat/indices?v'
```

Single-node may show **yellow** (replicas cannot be assigned) or **green** when `number_of_replicas: 0`. Both are fine for local learning.

---

## Vocabulary

| Term | Meaning | Hotel analogy |
|---|---|---|
| **Index** | Search “table” | `hotels_v1` |
| **Document** | One JSON row | One hotel |
| **Mapping** | Schema + analyzers | `es/hotels-index.json` |
| **Analyzer** | How text is tokenized | `"The Taj Café"` → `[the, taj, cafe]` |
| **`text` vs `keyword`** | Full-text vs exact | search vs filter/sort |
| **Alias** | Stable name over physical index | App uses `hotels`, data in `hotels_v1` |
| **Bulk API** | Many writes in one request | Reindex all hotels |
| **`_id`** | Document primary key | We use `hotelId` (upsert) |

---

## Part 1 — Index mapping

Defined in `es/hotels-index.json`. Explicit mapping + `dynamic: strict` (unknown fields are **rejected**, not guessed).

### text vs keyword

| Type | Good for | Example |
|---|---|---|
| `text` | Full-text / fuzzy | `q=taj` → The Taj Seaside |
| `keyword` | Exact filter / sort | `cityName.keyword = "Mumbai"` |

Multi-fields: `name` (text) + `name.keyword`.

### Custom analyzer `hotel_text_analyzer`

`standard` + `lowercase` + `asciifolding` → case- and accent-insensitive search.

### Denormalized fields

Search documents include `cityName` and `minNightlyPrice` (joined/computed from Postgres at sync time). ES does not JOIN tables at query time.

### Alias + versioned index

| Name | Role |
|---|---|
| `hotels_v1` | Physical index (data) |
| `hotels` | Alias used by the app |

Zero-downtime mapping change later: build `hotels_v2`, reindex, swing alias.

### Create / recreate index

```bash
curl -s -X PUT 'http://localhost:9200/hotels_v1' \
  -H 'Content-Type: application/json' --data-binary @es/hotels-index.json

curl -s 'http://localhost:9200/hotels/_mapping?pretty'
curl -s 'http://localhost:9200/_alias/hotels?pretty'
```

---

## Part 2 — Postgres → Elasticsearch sync

### Problem

An empty index cannot serve search. Data must be **copied** from Postgres into ES.

### Pattern A: full batch reindex

```text
POST /api/admin/search/hotels/reindex
        │
        ▼
JdbcTemplate → denormalized SQL (hotels + cities + MIN room price)
        │
        ▼
Bulk API → alias "hotels" (_id = hotelId, upsert, refresh=wait_for)
```

| Decision | Why |
|---|---|
| JDBC SQL (not JPA entity) | One projection query with JOIN + `MIN(price)` |
| `_id = hotelId` | Reindex is idempotent (update, not duplicate) |
| Index active + inactive | Search filters `active=true`; reactivation is simpler |
| Admin POST | Expensive rebuild — not a public API |

Use for: empty index bootstrap, disaster recovery, mapping rebuilds.

### Pattern B: Kafka-driven incremental sync (done)

```text
POST /api/admin/search/hotels/{id}/sync
  (or booking TX enqueues HotelUpserted)
        │
        ▼
Transactional outbox → hotel-events
        │
        ▼
HotelSearchEventConsumer → indexHotelById(hotelId) → ES upsert
```

Full reindex remains the “rebuild from scratch” tool; Kafka keeps the read model fresh between rebuilds.

### Verify sync

```bash
# Full rebuild
curl -s -X POST http://localhost:8080/api/admin/search/hotels/reindex | jq .
curl -s 'http://localhost:9200/hotels/_count?pretty'

# Incremental (outbox → Kafka → ES)
curl -s -X POST http://localhost:8080/api/admin/search/hotels/1/sync | jq .
```

Expected with seed data after full reindex: 5 documents.

---

## Part 3 — Search API

### Endpoint

```text
GET /api/search/hotels?q=&cityId=&minStars=&maxPrice=&sort=&page=&size=
```

### Query design

- **`multi_match`** on `name^3`, `cityName^2`, `description`, `addressLine`
- **Filters** in `bool.filter` (`active`, `cityId`, stars, price) — no scoring cost
- **Sort**: relevance, price, stars
- **Pagination**: `from = page × size`
- **Fuzzy**: exact match boosted + `fuzziness: AUTO` for typos
- **Errors**: ES down / missing index → **503** (booking API still works)

### Verify search

```bash
curl -s 'http://localhost:8080/api/search/hotels?q=taj' | jq .
curl -s 'http://localhost:8080/api/search/hotels?cityId=2&sort=price_asc' | jq .
curl -s 'http://localhost:8080/api/search/hotels?q=tajj' | jq .   # typo still finds Taj
```

---

## Key classes

| Class | Where | Role |
|---|---|---|
| `HotelSearchDocument` | search-service | ES document shape (denormalized) |
| `HotelSearchSyncService` | search-service | Postgres → bulk index + `indexHotelById` |
| outbox + `HotelUpserted` | booking-service | Enqueue catalog changes via outbox |
| `HotelSearchEventConsumer` | search-service | Kafka → ES upsert |
| `HotelSearchAdminController` | backend (proxy) + search-service | `POST .../reindex`, `POST .../{id}/sync` |
| `HotelSearchService` | search-service | ES queries |
| `HotelSearchController` | backend (proxy) | `GET /api/search/hotels` |
| `HotelSearchCriteria` / `HotelSearchSort` | search-service | Filters, sort, page |
| `SearchServiceUnavailableException` | backend | Maps to HTTP 503 |

---

## Inspect Elasticsearch

```bash
# Health & indices
curl -s 'http://localhost:9200/_cluster/health?pretty'
curl -s 'http://localhost:9200/_cat/indices?v'
curl -s 'http://localhost:9200/_cat/aliases?v'

# Count / sample docs
curl -s 'http://localhost:9200/hotels/_count?pretty'
curl -s 'http://localhost:9200/hotels/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{"query":{"match_all":{}},"size":2}'

# Analyzer test
curl -s -X POST 'http://localhost:9200/hotels/_analyze?pretty' \
  -H 'Content-Type: application/json' \
  -d '{"analyzer":"hotel_text_analyzer","text":"The Taj Seaside Café"}'
```

Kibana Dev Tools: [http://localhost:5601](http://localhost:5601)

---

## Production / interview notes

| Local | Booking.com-scale |
|---|---|
| Batch reindex + Kafka `HotelUpserted` | Same patterns + CDC optional |
| 1 node | Multi-node cluster, replicas, snapshots |
| search-service (extracted) | Same + dedicated cluster / SRE ownership |
| Alias `hotels` | Blue/green reindex on mapping changes |
| 503 if ES down | Circuit breakers, degraded UX, core booking unaffected |

**HLD takeaway:** polyglot persistence — each store owns one concern (truth / cache / search).

---

## Build history (how we learned it)

| Step | What we added |
|---|---|
| 1 | Docker ES + Kibana |
| 2 | Mapping `hotels_v1` + alias `hotels` |
| 3 | Batch reindex admin API |
| 4 | Basic `GET /api/search/hotels?q=` |
| 5 | Filters, sort, pagination |
| 6 | Fuzzy + 503 / validation polish |
| 7 | Kafka-driven incremental sync (`hotel-events`) |

---

## Related docs

- Schema: `docs/database-schema-postgres.md`
- Redis (cache/locks): `docs/redis.md`
- Kafka (outbox + hotel-events): `docs/kafka.md`
