# Elasticsearch Search Service

PostgreSQL remains the **source of truth**. Elasticsearch is a **search read model** rebuilt from Postgres when needed.

## Roadmap

| Milestone | Status | What |
|---|---|---|
| 1. Docker ES + Kibana | ✅ | Local cluster, health checks |
| 2. Index mappings | ✅ | `hotels_v1` index + `hotels` alias |
| 3. Postgres → ES sync | ✅ | Batch reindex via admin API |
| 4. Search API (basic) | ✅ | `GET /api/search/hotels?q=` |
| 5. Filters, sort, pagination | ✅ | city, stars, price |
| 6. Fuzzy + production polish | ✅ | typos, error handling |

---

## Milestone 1 — Docker infrastructure

### Start the stack

```bash
docker compose up -d
docker compose ps
```

You should see `hms_elasticsearch` (healthy) and `hms_kibana` (running).

### Verify Elasticsearch

```bash
# Cluster health (yellow is OK for single-node — no replica shards)
curl -s 'http://localhost:9200/_cluster/health?pretty'

# Node info
curl -s http://localhost:9200

# List indices (empty until Milestone 2/3 — ignore .internal.* system indices)
curl -s 'http://localhost:9200/_cat/indices?v'
```

Expected health response (key fields):

```json
{
  "cluster_name" : "docker-cluster",
  "status" : "yellow",
  "number_of_nodes" : 1
}
```

**Why `yellow` not `green`?** On a single node, replica shards cannot be assigned — Elasticsearch still works; this is normal for local dev.

### Kibana (optional UI)

Open in browser: [http://localhost:5601](http://localhost:5601)

Use **Dev Tools** → Console to run:

```json
GET _cluster/health
```

### Architecture (current)

```text
PostgreSQL (source of truth)     Redis (cache + locks)
        │                              │
        │  (sync — Milestone 3)        │
        ▼                              │
Elasticsearch (search read model) ◄────┘
        ▲
        │  (search queries — Milestone 4+)
   Spring Boot Search API
```

### Docker config explained

| Setting | Purpose |
|---|---|
| `discovery.type=single-node` | One ES node; no cluster formation |
| `xpack.security.enabled=false` | No TLS/password for local dev only |
| `ES_JAVA_OPTS=-Xms512m -Xmx512m` | Limit RAM usage |
| `hms_esdata` volume | Persist index data across restarts |
| `healthcheck` on `/_cluster/health` | Compose waits until ES is queryable |

### Stop / reset

```bash
docker compose down          # keeps ES index data in volume
docker compose down -v       # wipes ALL volumes including ES + Postgres data
```

---

## Milestone 2 — Index mapping design

The mapping is the search schema. Defined explicitly in `es/hotels-index.json` — never rely on
Elasticsearch's dynamic guessing for a production index.

### text vs keyword (the core decision)

| Type | Analyzed? | Good for | Example |
|---|---|---|---|
| `text` | Yes (tokenized) | Full-text / fuzzy search | search "taj" → "The Taj Seaside" |
| `keyword` | No (exact) | Filter, sort, aggregations | `cityName = "Mumbai"` |

Fields that need both use a **multi-field**: `name` (text) + `name.keyword` (keyword).

### Custom analyzer: `hotel_text_analyzer`

`standard` tokenizer + `lowercase` + `asciifolding`:

```text
"The Taj Seaside Café"  →  [the, taj, seaside, cafe]
```

So searches are case-insensitive and accent-insensitive.

### Field design (denormalized from hotels + cities + rooms)

| Field | Type | Purpose |
|---|---|---|
| `hotelId` | long | Identity → look up real row in Postgres |
| `name` | text + keyword | Search + sort/exact |
| `description` | text | Full-text search |
| `addressLine` | text | Searchable |
| `cityId` | long | Exact filter |
| `cityName` | text + keyword | Search + filter/sort (denormalized) |
| `starRating` | integer | Range filter + sort |
| `minNightlyPrice` | scaled_float (×100) | Range filter + sort (from rooms) |
| `active` | boolean | Filter inactive hotels |
| `createdAt` / `updatedAt` | date | Tie-break sort, freshness |

Notes:
- **`scaled_float`** stores money as an integer internally — no float rounding.
- **`dynamic: strict`** rejects unexpected fields (catches sync bugs early).

### Alias + versioned index (zero-downtime pattern)

Physical index is **`hotels_v1`**; the app uses the alias **`hotels`**.
To change the mapping later: build `hotels_v2`, reindex, then swing the alias atomically.

```bash
# Create index + alias from the mapping file
curl -s -X PUT 'http://localhost:9200/hotels_v1' \
  -H 'Content-Type: application/json' \
  --data-binary @es/hotels-index.json

# Verify
curl -s 'http://localhost:9200/_cat/indices/hotels_v1?v'
curl -s 'http://localhost:9200/hotels/_mapping?pretty'
curl -s 'http://localhost:9200/_alias/hotels?pretty'

# Test the analyzer
curl -s -X POST 'http://localhost:9200/hotels/_analyze?pretty' \
  -H 'Content-Type: application/json' \
  -d '{"analyzer":"hotel_text_analyzer","text":"The Taj Seaside Café"}'
```

`number_of_replicas: 0` keeps a single-node index **green** (replicas can't be assigned with one node).

### Recreate the index (if needed)

```bash
curl -s -X DELETE 'http://localhost:9200/hotels_v1'
curl -s -X PUT 'http://localhost:9200/hotels_v1' \
  -H 'Content-Type: application/json' --data-binary @es/hotels-index.json
```

---

## Milestone 3 — Postgres → Elasticsearch sync

### Business problem

The `hotels` index is empty until we copy data from Postgres. Search cannot work without sync.

### Design: full batch reindex (not Kafka yet)

| Approach | When | This project |
|---|---|---|
| **Full reindex** | Rebuild entire index from Postgres | ✅ Milestone 3 |
| **Incremental sync** | Update one hotel on change | Later (or via Kafka) |

Flow:

```text
POST /api/admin/search/hotels/reindex
        │
        ▼
JdbcTemplate → denormalized SQL (hotels + cities + min room price)
        │
        ▼
Bulk API → index into alias "hotels" (_id = hotelId, upsert)
```

### Why `_id = hotelId`?

Re-running reindex **updates** existing documents instead of duplicating them — idempotent and safe to retry.

### Denormalized SQL (why not JPA entities?)

One query joins `hotels`, `cities`, and `MIN(rooms.nightly_price)` — exactly what the search document needs. JPA entities would require multiple queries or DTO projections; JDBC keeps the sync SQL explicit.

### Verify sync

```bash
# Start app (Gradle — not IDE bin/)
cd backend && ./gradlew bootRun

# Trigger reindex
curl -s -X POST http://localhost:8080/api/admin/search/hotels/reindex | jq .

# Expected: readFromPostgres=5, indexed=5, failures=0 (seed data has 5 hotels)

# Confirm documents in ES
curl -s 'http://localhost:9200/hotels/_count?pretty'
curl -s 'http://localhost:9200/hotels/_search?pretty' -H 'Content-Type: application/json' \
  -d '{"query":{"match_all":{}},"size":3}'
```

### Integration test

```bash
./gradlew test --tests "com.hotelbooking.search.HotelSearchSyncIT"
```

Requires Postgres + Elasticsearch + Redis running.

---

## Milestone 4 — Basic search API

### Business problem

Users need to **discover** hotels by name, city, or description — not just fetch by ID.
Postgres `LIKE` queries do not scale; Elasticsearch is the query engine here.

### Architecture

```text
GET /api/search/hotels?q=taj
        │
        ▼
HotelSearchService → Elasticsearch (alias "hotels")
        │
        ▼
JSON hits (denormalized snapshot — no Postgres on this path)
```

**Important:** Search reads ES only. Booking and hotel detail APIs still use Postgres.

### Query design

```json
{
  "query": {
    "bool": {
      "must": {
        "multi_match": {
          "query": "taj",
          "fields": ["name^3", "cityName^2", "description", "addressLine"],
          "type": "best_fields"
        }
      },
      "filter": [{ "term": { "active": true } }]
    }
  }
}
```

| Piece | Why |
|---|---|
| `multi_match` | Search across several text fields at once |
| `name^3` | Boost hotel name (3× relevance) |
| `cityName^2` | City is second-most important |
| `filter: active=true` | Inactive hotels indexed but hidden from search |
| Blank `q` | `match_all` + active filter → list all active hotels |

### Verify search

```bash
# Reindex first (if index is empty)
curl -s -X POST http://localhost:8080/api/admin/search/hotels/reindex | jq .

# Search by hotel name
curl -s 'http://localhost:8080/api/search/hotels?q=taj' | jq .

# Search by city
curl -s 'http://localhost:8080/api/search/hotels?q=mumbai' | jq .
curl -s 'http://localhost:8080/api/search/hotels?q=delhi' | jq .

# List all active hotels (no query)
curl -s 'http://localhost:8080/api/search/hotels' | jq .
```

Expected for `q=taj`: one hit — **The Taj Seaside** in Mumbai.

### Integration test

```bash
./gradlew test --tests "com.hotelbooking.search.HotelSearchIT"
```

---

## Milestone 5 — Filters, sort, pagination

### Business problem

Users rarely search by text alone. Real hotel search combines:
- **Filters** — city, minimum stars, maximum price
- **Sort** — cheapest first, highest rated, or relevance
- **Pagination** — page through large result sets

### API parameters

| Param | Type | Example | ES clause |
|---|---|---|---|
| `q` | text | `taj` | `multi_match` (must) |
| `cityId` | long | `2` | `term` on `cityId` (filter) |
| `minStars` | 1–5 | `4` | `range` on `starRating` ≥ (filter) |
| `maxPrice` | number | `5000` | `range` on `minNightlyPrice` ≤ (filter) |
| `sort` | enum | `price_asc` | sort clause |
| `page` | int ≥ 0 | `0` | `from = page × size` |
| `size` | 1–100 | `20` | `size` |

**Sort values:** `relevance` (default), `price_asc`, `price_desc`, `stars_desc`, `stars_asc`

### Why filters go in `bool.filter` (not `must`)

Filters do **not** affect relevance scoring — they only include/exclude documents.
That is the standard pattern for faceted search (city, price range, stars).

### Verify filters and pagination

```bash
# Delhi only (cityId=2)
curl -s 'http://localhost:8080/api/search/hotels?cityId=2' | jq .

# 5-star hotels only
curl -s 'http://localhost:8080/api/search/hotels?minStars=5' | jq .

# Budget hotels (cheapest room ≤ 5000)
curl -s 'http://localhost:8080/api/search/hotels?maxPrice=5000' | jq .

# Cheapest first
curl -s 'http://localhost:8080/api/search/hotels?sort=price_asc&size=3' | jq .

# Page 2 of results (0-based page index)
curl -s 'http://localhost:8080/api/search/hotels?sort=price_asc&page=1&size=2' | jq .

# Combine text + filters
curl -s 'http://localhost:8080/api/search/hotels?q=grand&minStars=4' | jq .
```

Expected:
- `cityId=2` → **Capital Grand** only
- `minStars=5` → **The Taj Seaside** + **Manhattan Plaza**
- `maxPrice=5000` → 3 hotels (Garden City, Capital Grand, Taj)
- `sort=price_asc&size=1` → **Garden City Inn** (₹2200)

### Integration test

```bash
./gradlew test --tests "com.hotelbooking.search.HotelSearchIT"
```

---

## Milestone 6 — Fuzzy search + production polish

### Business problem

Users make typos (`tajj`, `manhatan`, `bangalor`). Strict matching returns zero results —
bad UX. Production search tolerates typos while still ranking exact matches higher.

### Fuzzy query design

Two `should` clauses inside a `bool` (at least one must match):

```json
{
  "bool": {
    "should": [
      { "multi_match": { "query": "tajj", "fields": ["name^3", ...], "boost": 2 } },
      { "multi_match": { "query": "tajj", "fields": ["name^3", ...], "fuzziness": "AUTO" } }
    ],
    "minimum_should_match": 1
  }
}
```

| Setting | Meaning |
|---|---|
| `fuzziness: AUTO` | 0 edits for 1–2 char terms, 1 for 3–5, 2 for 6+ |
| Exact clause `boost: 2` | Correct spellings outrank fuzzy matches |
| `fuzzy-enabled: true` | Toggle in `application.yml` |

### Production error handling

| Failure | HTTP | Client message |
|---|---|---|
| ES down / connection refused | **503** | Search service is temporarily unavailable |
| Index missing | **503** | Run reindex admin endpoint |
| Query too long (>200 chars) | **400** | Query exceeds maximum length |
| Invalid sort param | **400** | Allowed sort values listed |

**Why 503 not 500?** Search is a separate read model — if ES is down, the booking
API still works. Clients can retry search; a 503 signals transient failure.

### Verify fuzzy search

```bash
# Typo in hotel name (extra "j")
curl -s 'http://localhost:8080/api/search/hotels?q=tajj' | jq .

# Misspelled city
curl -s 'http://localhost:8080/api/search/hotels?q=bangalor' | jq .

# Misspelled hotel name
curl -s 'http://localhost:8080/api/search/hotels?q=manhatan' | jq .

# Query too long → 400
curl -s -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8080/api/search/hotels?q='"$(python3 -c 'print("a"*201)')"
```

Expected: typos still return the correct hotels; long query returns `400`.

### Simulate ES unavailable (optional)

```bash
docker compose stop elasticsearch
curl -s 'http://localhost:8080/api/search/hotels?q=taj' | jq .   # → 503
docker compose start elasticsearch
```

### Integration test

```bash
./gradlew test --tests "com.hotelbooking.search.HotelSearchIT"
```

---

## Elasticsearch track complete

You now have a production-style search read model:

```text
PostgreSQL (source of truth)
        │ batch reindex
        ▼
Elasticsearch (hotels alias)  ←── GET /api/search/hotels
```

**Next up in the roadmap:** Kafka for event-driven sync, then microservices decomposition.
