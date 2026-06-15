# Hotel Booking System — Performance & Scaling at 50M Bookings

> Companion to [`database-design.md`](./database-design.md) and [`database-schema-postgres.md`](./database-schema-postgres.md).
>
> **Role:** database performance engineer. **Goal:** make the schema fast and stable at real scale.
>
> **Assumed scale:**
> | Entity | Rows | Notes |
> |---|---|---|
> | users | 10,000,000 | ~10M |
> | hotels | 100,000 | ~100K |
> | rooms | ~2,000,000 | assume ~20 rooms/hotel |
> | bookings | 50,000,000 | ~50M — the hot table |
> | payments | ~60,000,000 | ≥1 per booking (retries, refunds) |
> | reviews | ~15,000,000 | roughly 1 per completed stay |
>
> At this size, "it works on my laptop" stops being the bar. A single bad query plan can read tens of millions of rows and take minutes. Everything below is about **never touching rows you don't need.**

---

## Table of Contents
1. [Mental model: what actually costs time](#1-mental-model-what-actually-costs-time)
2. [Task 1 — Recommended indexes](#2-task-1--recommended-indexes)
3. [Task 2 — Query bottlenecks](#3-task-2--query-bottlenecks)
4. [Task 3 — How bookings are searched efficiently](#4-task-3--how-bookings-are-searched-efficiently)
5. [Task 4 — Covering indexes](#5-task-4--covering-indexes)
6. [Task 5 — Composite indexes](#6-task-5--composite-indexes)
7. [Task 6 — Partitioning possibilities](#7-task-6--partitioning-possibilities)
8. [Task 7 — How Booking.com-scale systems handle search](#8-task-7--how-bookingcom-scale-systems-handle-search)
9. [Reading an EXPLAIN plan](#9-reading-an-explain-plan)
10. [Cheat sheet](#10-cheat-sheet)

---

## 1. Mental model: what actually costs time

Three numbers dominate database performance, in increasing order of pain:

1. **Sequential scan** — reading every row of a table. On 50M bookings, that's ~50M row reads + disk I/O. Catastrophic for a single user-facing query, acceptable only for analytics/batch.
2. **Random I/O** — jumping around the disk/heap to fetch rows an index pointed at. Each "heap fetch" is cheap individually but deadly in bulk.
3. **Sorting & hashing large sets** — `ORDER BY` / `GROUP BY` / `DISTINCT` / hash joins over millions of rows spill to disk if they exceed `work_mem`.

**Every optimization in this doc reduces one of these three.** The golden rule:

> The fastest query is one that uses an index to read **only the rows it returns**, in the **order it needs them**, **without touching the heap** if possible.

That sentence encodes: good `WHERE` indexing (fewer rows), matching `ORDER BY` (no sort), and covering indexes (no heap fetch). The rest is detail.

---

## 2. Task 1 — Recommended indexes

The existing `03_indexes.sql` is a good start. Here is the **scale-hardened** set, with the reasoning. (`b-tree` unless stated otherwise.)

### Already justified (keep)

| Index | Columns | Serves |
|---|---|---|
| PK indexes | every `*_id` | joins, lookups by id |
| `users.email` (UNIQUE) | `email` | login |
| `uq_rooms_hotel_number` | `(hotel_id, room_number)` | uniqueness + "rooms in hotel" |
| `uq_payments_provider_txn` | `(provider, provider_txn_id)` | webhook idempotency |
| `uq_bookings_reference` | `booking_reference` | support lookup by code |

### Critical at scale (add / confirm)

| Index | Columns | Why it's mandatory at 50M |
|---|---|---|
| **Availability** | `bookings (room_id, check_in_date, check_out_date) WHERE status IN ('PENDING','CONFIRMED','COMPLETED')` | The #1 hot query. Partial + composite. See §4. |
| **GiST overlap** | `bookings USING gist (room_id, daterange(...))` — already implied by the `EXCLUDE` constraint | The exclusion constraint **is** a GiST index; it accelerates overlap checks *and* enforces no-double-booking. |
| **User history** | `bookings (user_id, created_at DESC)` | "my bookings, newest first" for 10M users. |
| **Booking status ops** | `bookings (status, check_in_date) WHERE status IN ('PENDING','CONFIRMED')` | Partial index — tiny, hot. "Today's arrivals", "expire stale pending". |
| **Payments by booking** | `payments (booking_id, created_at DESC)` | Payment history per booking. |
| **Reviews by hotel** | `reviews (hotel_id, created_at DESC)` | Hotel page "latest reviews". |
| **Reviews aggregate** | `reviews (hotel_id, rating)` | "avg rating per hotel" — can be covering (see §5). |
| **Hotel search** | `hotels (city_id, star_rating DESC) WHERE is_active` | "active 4★+ hotels in city X". |
| **Rooms by hotel/type** | `rooms (hotel_id, room_type_id) WHERE is_active` | Drill-down inside a hotel. |

### Index hygiene at scale

- **Don't over-index the write-hot table.** Every index on `bookings` and `payments` slows every insert/update and bloats storage. Each new booking must update *all* booking indexes. Keep them lean and query-justified.
- **Use partial indexes aggressively.** Terminal-state rows (`CANCELLED`, `NO_SHOW`, old `COMPLETED`) don't belong in the hot availability index. `WHERE status IN (...)` shrinks the index by the fraction of dead rows — often 30–50% smaller, and it stays in RAM.
- **Watch index bloat.** High-churn tables need periodic `REINDEX CONCURRENTLY` / autovacuum tuning.
- **Build indexes with `CREATE INDEX CONCURRENTLY`** in production so you don't lock the table for the duration.

---

## 3. Task 2 — Query bottlenecks

The predictable hot spots at this scale, and the fix for each:

### B1. Availability search ("is this room/hotel free for these dates?")
- **Bottleneck:** naive `WHERE check_in_date <= X AND check_out_date >= Y` with no usable index → sequential scan of 50M rows.
- **Fix:** GiST range index + `daterange` overlap (`&&`), or the composite partial b-tree on `(room_id, check_in_date, check_out_date)`. See §4.

### B2. "My bookings" for a power user
- **Bottleneck:** `WHERE user_id = ? ORDER BY created_at DESC LIMIT 20` → if only `user_id` is indexed, DB fetches all of that user's bookings then sorts.
- **Fix:** composite `(user_id, created_at DESC)` so rows come back **pre-sorted**; `LIMIT 20` then stops early. No sort step.

### B3. Aggregations over the whole booking/payment history
- **Bottleneck:** "total revenue per hotel for last year" scans tens of millions of payment rows + multi-join.
- **Fix:** (a) date-filter on an indexed column to prune partitions; (b) **pre-aggregate** into summary tables / materialized views refreshed nightly; (c) push to an OLAP store (see §8).

### B4. Counting/`OFFSET` pagination
- **Bottleneck:** `LIMIT 20 OFFSET 1000000` still reads and discards 1M rows. `SELECT COUNT(*)` over 50M rows is a full scan.
- **Fix:** **keyset (seek) pagination** — `WHERE (created_at, booking_id) < (:last_ts, :last_id) ORDER BY created_at DESC, booking_id DESC LIMIT 20`. Constant time regardless of depth. For counts, use approximate counts (`reltuples` from `pg_class`) when exactness isn't required.

### B5. The double-booking write path under contention
- **Bottleneck:** thousands of concurrent booking attempts on popular rooms/dates serialize on the same rows/ranges.
- **Fix:** the `EXCLUDE` GiST constraint handles correctness; to reduce contention, keep transactions short, write the booking row last, and consider queueing hot-inventory writes. See §8.

### B6. `NOT IN`, functions on columns, implicit casts
- **Bottleneck:** `WHERE DATE(check_in_date) = ...` or `WHERE user_id::text = ...` defeat indexes (the index is on the raw column, not the function/cast result). `NOT IN (subquery)` can also force poor plans.
- **Fix:** keep columns "bare" on the left of predicates; index expressions explicitly if you must filter on `lower(email)` etc.; prefer `NOT EXISTS`.

---

## 4. Task 3 — How bookings are searched efficiently

The central question of a booking system: **"Which rooms in hotel H are free between check-in D1 and check-out D2?"** A room is free if **no active booking overlaps** `[D1, D2)`.

### The overlap predicate
Two date ranges `[a, b)` and `[c, d)` overlap iff `a < d AND c < b`. So "find conflicting bookings for a room":

```sql
SELECT 1
FROM bookings
WHERE room_id = :room
  AND status IN ('PENDING','CONFIRMED','COMPLETED')
  AND check_in_date < :d2          -- existing.start < requested.end
  AND check_out_date > :d1         -- existing.end   > requested.start
LIMIT 1;
```

If that returns nothing, the room is free.

### Why PostgreSQL ranges + GiST win here
Instead of two separate comparisons, model the stay as a `daterange` and ask for overlap with one operator:

```sql
WHERE room_id = :room
  AND daterange(check_in_date, check_out_date, '[)') && daterange(:d1, :d2, '[)')
```

The `&&` ("overlaps") operator is backed by a **GiST index** (the same one the `EXCLUDE` constraint creates). GiST is purpose-built for "find ranges that intersect this range" — it prunes the search tree instead of scanning. This is the difference between **O(log n + matches)** and **O(n)**.

### Searching a whole hotel (all rooms at once)
"Show available rooms in hotel H for D1–D2":

```sql
SELECT r.room_id, r.room_number, r.nightly_price
FROM rooms r
WHERE r.hotel_id = :hotel
  AND r.is_active
  AND NOT EXISTS (
      SELECT 1 FROM bookings b
      WHERE b.room_id = r.room_id
        AND b.status IN ('PENDING','CONFIRMED','COMPLETED')
        AND daterange(b.check_in_date, b.check_out_date, '[)')
            && daterange(:d1, :d2, '[)')
  );
```

- Outer query uses `rooms (hotel_id) WHERE is_active` → a few dozen rooms.
- For each, `NOT EXISTS` probes the GiST/partial index on `bookings` → tiny, indexed lookups.
- The work is proportional to **rooms in the hotel**, not bookings in the system.

### The real-world shortcut: precomputed availability
At Booking.com scale, you don't hit the transactional `bookings` table for *browsing*. You maintain a **room-night availability** structure (e.g., a table keyed `(room_id, date)` or a per-room calendar bitmap) updated when bookings are made/cancelled. Reads become point lookups. The transactional overlap check is still the **source of truth at write time** (to prevent double-booking), but reads are served from the denormalized calendar/cache. See §8.

---

## 5. Task 4 — Covering indexes

### The problem they solve: the heap fetch
A normal b-tree index stores the indexed columns + a pointer to the table row (the "heap"). If your query needs columns **not** in the index, the DB must jump to the heap for each match — **random I/O**, the expensive kind. Over millions of matches, heap fetches dominate.

### Definition
A **covering index** contains *all columns the query needs* — both for filtering and for output — so the query is answered **entirely from the index**, never touching the heap. PostgreSQL calls this an **index-only scan**.

### PostgreSQL syntax: `INCLUDE`
Put the columns you **filter/sort by** in the key, and the columns you only **return** in `INCLUDE` (non-key payload):

```sql
-- Query: average rating per hotel
-- SELECT hotel_id, AVG(rating) FROM reviews GROUP BY hotel_id;
CREATE INDEX idx_reviews_hotel_rating_cov
    ON reviews (hotel_id) INCLUDE (rating);
```

Now the aggregate reads only the index: `hotel_id` to group, `rating` to average. No heap. On 15M reviews that's a massive win.

Another example — "my bookings" list showing reference + dates:

```sql
CREATE INDEX idx_bookings_user_cov
    ON bookings (user_id, created_at DESC)
    INCLUDE (booking_reference, check_in_date, check_out_date, total_amount, status);
```

The list page renders straight from the index.

### Caveats
- **Index-only scans require the visibility map to be current** — keep autovacuum healthy, or PostgreSQL still peeks at the heap to check row visibility.
- **Don't stuff every column into `INCLUDE`.** A fat index costs write speed and RAM. Cover only your genuinely hot, high-frequency queries.
- Covering indexes shine for **read-heavy** access paths; on the write-hot `bookings` table, weigh the insert cost.

---

## 6. Task 5 — Composite indexes

A **composite (multi-column) index** indexes several columns in a defined order. The order is everything.

### The left-prefix rule
An index on `(a, b, c)` can be used for queries that filter on:
- `a`
- `a, b`
- `a, b, c`

…but **not** `b` alone, or `c` alone, or `b, c`. It's like a phone book sorted by (last name, first name): great for "Sharma, A"; useless for finding everyone named "Aarav".

### Column ordering rules (in priority)
1. **Equality columns before range columns.** The index can only "range-scan" on the *last* used column. Example — availability:
   ```sql
   bookings (room_id, check_in_date, check_out_date)
   ```
   `room_id` is `=` (equality), dates are ranges. If you put a date first, the `room_id` equality can't be used efficiently.
2. **Most selective / most common filter first** (among equality columns) — narrows the search fastest and serves the most queries via the left-prefix.
3. **Match `ORDER BY` direction** to avoid a sort. `(user_id, created_at DESC)` serves `WHERE user_id=? ORDER BY created_at DESC` with **zero sorting**.

### Worked example
```sql
-- Serves: WHERE user_id = ? ORDER BY created_at DESC LIMIT n
CREATE INDEX idx_bookings_user_created ON bookings (user_id, created_at DESC);
```
- `user_id` equality → jump straight to that user's slice.
- Within the slice, rows are already ordered by `created_at DESC` → `LIMIT n` stops immediately.
- One index, no sort, early termination. This is the template for almost every "list X's recent Y" feature.

### Composite vs covering — how they combine
You often want both: a composite **key** (for filter + sort) plus `INCLUDE` columns (to avoid the heap). That's the `idx_bookings_user_cov` example in §5 — composite *and* covering.

---

## 7. Task 6 — Partitioning possibilities

At 50M+ rows, a single physical table becomes unwieldy: indexes are huge, vacuum is slow, old data clogs the hot path. **Partitioning** splits one logical table into many physical sub-tables ("partitions"). PostgreSQL routes queries to only the relevant partitions ("partition pruning").

### Best candidate: `bookings` by date (range partitioning)
Partition by `check_in_date` (or `created_at`) into months or years:

```sql
CREATE TABLE bookings (...) PARTITION BY RANGE (check_in_date);

CREATE TABLE bookings_2026 PARTITION OF bookings
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE bookings_2025 PARTITION OF bookings
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
-- ... one per period
```

**Wins:**
- **Pruning:** "bookings in June 2026" touches one partition, not 50M rows.
- **Cheap archival:** drop/detach an old year in milliseconds (`DETACH PARTITION`) instead of a giant `DELETE`.
- **Smaller indexes per partition** → more stays in RAM, faster vacuum.
- **Recent data stays hot;** cold partitions can live on cheaper storage.

**Costs / caveats:**
- The **partition key should appear in most queries**, or you lose pruning and query *all* partitions.
- Unique constraints must include the partition key — so `booking_reference` uniqueness needs care (use a global lookup or include the key).
- The `EXCLUDE` overlap constraint interacts with partitioning — typically enforced per-partition; design the key so a room's overlapping bookings land in the same partition (date-based partitioning by `check_in_date` mostly does, but cross-boundary stays need thought).

### Other candidates
- **`payments`** — range-partition by `created_at` (same archival logic; financial data is naturally time-series and retention-regulated).
- **`bookings` by `hotel_id` (hash partitioning)** — if access is overwhelmingly per-hotel rather than per-date. Spreads write load; less useful for date-range analytics.
- **`reviews`** — usually fine unpartitioned; partition by `created_at` only if it grows huge.

### Rule of thumb
Partition the **largest, most time-oriented, append-heavy** tables (`bookings`, `payments`) by **date**, because (a) queries are date-scoped, and (b) old data is archived as whole partitions.

---

## 8. Task 7 — How Booking.com-scale systems handle search

Real OTAs (Booking.com, Expedia, Airbnb) **do not** serve hotel search from the transactional OLTP database. The architecture splits hard along **read vs write**.

### 8.1 Separate the search path from the booking path
- **Search/browse (99% of traffic, read-only):** served by a **dedicated search engine** — Elasticsearch / OpenSearch / Solr — holding **denormalized hotel documents** (name, location, amenities, price summaries, rating, popularity). Supports full-text, typo tolerance, geo radius, faceting (stars, price bands, amenities), and relevance ranking. The relational DB is *not* in this path.
- **Booking (1% of traffic, read-write):** the transactional PostgreSQL cluster, where the `EXCLUDE` overlap constraint and payment idempotency guarantee correctness. This is the source of truth.

### 8.2 Precomputed availability / pricing
- A **room-night availability service** maintains, per room/rate-plan, which dates are open and at what price — often as compact per-room **calendars/bitmaps** in a fast KV store (Redis/Cassandra).
- Updated **asynchronously** when bookings/cancellations happen (via change-data-capture or an event stream like Kafka).
- Browsing checks the calendar (point lookups, milliseconds). The authoritative overlap check only runs at the **moment of booking** to prevent double-booking.

### 8.3 Geo + faceted search
- Hotels are indexed with **geo coordinates**; "hotels within 5 km of this pin" uses geo indexes (Elasticsearch geo, or PostGIS in the DB). Plain b-trees can't do 2D radius queries efficiently.
- Facets (price, stars, amenities) are aggregations the search engine computes on the fly over the result set.

### 8.4 Caching layers
- **CDN / edge cache** for static and semi-static content.
- **Redis/Memcached** for hot lookups: popular hotel pages, room-type metadata, search results for common queries (city + dates).
- **Cache invalidation** driven by the event stream when prices/availability change.

### 8.5 Read replicas & CQRS
- **Read replicas** of PostgreSQL absorb reporting and secondary reads; writes go to the primary.
- **CQRS** (Command Query Responsibility Segregation): the write model (normalized, constraint-heavy) differs from the read model (denormalized documents/calendars). Events propagate writes into read models.

### 8.6 Sharding the transactional store
- Beyond one primary's capacity, shard bookings/payments — commonly **by hotel_id** (keeps a hotel's inventory together) or by geography. Cross-shard analytics moves to a **data warehouse** (BigQuery/Snowflake/Redshift) fed by ETL/CDC.

### 8.7 The end-to-end picture
```
            ┌─────────────┐     search/browse      ┌────────────────────┐
  Users ───▶│   CDN/Edge  │───────────────────────▶│  Elasticsearch     │  (denormalized
            └─────────────┘                         │  + Redis calendars │   hotel docs,
                  │                                  └─────────┬──────────┘   availability)
                  │ book                                       ▲
                  ▼                                            │ async events (Kafka/CDC)
            ┌─────────────────────────────┐                   │
            │  PostgreSQL (OLTP primary)   │───────────────────┘
            │  bookings/payments + EXCLUDE │
            │  partitioned by date         │──▶ read replicas ──▶ Data Warehouse (OLAP)
            └─────────────────────────────┘
```

**The lesson for you:** your normalized PostgreSQL schema with the `EXCLUDE` constraint is exactly right as the **source of truth**. Scaling is about putting the right **read-optimized projections** (search index, availability cache, replicas, warehouse) *in front of* it — not about denormalizing the source of truth itself.

---

## 9. Reading an EXPLAIN plan

The one skill that ties this all together. `EXPLAIN ANALYZE` shows what the planner *actually did*.

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.room_id
FROM rooms r
WHERE r.hotel_id = 42 AND r.is_active
  AND NOT EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.room_id = r.room_id
      AND daterange(b.check_in_date, b.check_out_date, '[)')
          && daterange(DATE '2026-07-01', DATE '2026-07-05', '[)')
      AND b.status IN ('PENDING','CONFIRMED','COMPLETED')
  );
```

What to look for:
- **`Seq Scan` on a big table** = red flag (missing/unused index). Want `Index Scan` / `Index Only Scan` / `Bitmap Index Scan`.
- **`Index Only Scan`** = covering index working (no heap). 
- **`rows=` estimate vs `actual rows`** wildly off = stale statistics → `ANALYZE` the table.
- **`Heap Fetches:` high** on an index-only scan = visibility map stale → vacuum.
- **`Sort` / `Sort Method: external merge Disk`** = sort spilling to disk → add a matching index or raise `work_mem`.
- **`Nested Loop` over many rows** vs **`Hash Join`** — nested loops are great for few rows, terrible for millions; the planner picks based on stats (keep them fresh).

> Workflow: write query → `EXPLAIN ANALYZE` → find the seq scan / sort / heap fetch → add the index that removes it → re-run → confirm the node changed. Iterate.

---

## 10. Cheat sheet

| Situation | Reach for |
|---|---|
| Filter by equality + sort by time | composite `(eq_col, time_col DESC)` |
| Query needs only a few columns | covering index with `INCLUDE` |
| "Free rooms for these dates?" | GiST + `daterange &&` (already enforced by `EXCLUDE`) |
| Deep pagination | keyset/seek pagination, not `OFFSET` |
| Huge time-series table | range-partition by date |
| Dead/terminal rows polluting an index | partial index `WHERE status IN (...)` |
| "Find rows with no match" | `NOT EXISTS` (not `NOT IN`) |
| Heavy analytics | summary tables / materialized views / OLAP warehouse |
| Hotel search, geo, facets | external search engine (Elasticsearch) |
| Browsing availability at scale | precomputed availability calendar + cache |
| Too many writes for one node | shard by hotel_id; replicas for reads |
| "Why is this slow?" | `EXPLAIN (ANALYZE, BUFFERS)` |

### Top 5 takeaways
1. **Index for the query, not the column.** Composite order = equality → range → sort-match.
2. **Covering indexes kill heap fetches** — the silent scale killer.
3. **The `EXCLUDE` GiST constraint is both your correctness guarantee and your availability index.** Lean on it.
4. **Partition the big time-series tables by date** for pruning + painless archival.
5. **Don't scale the source of truth by denormalizing it** — put read-optimized projections (search index, availability cache, replicas, warehouse) in front of a clean, normalized, constraint-rich core.
