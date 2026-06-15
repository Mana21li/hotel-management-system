# Hotel Booking System — Relational Database Design

> A walk-through of the database design for a Hotel Booking System, written as if a senior architect were explaining it to a junior engineer preparing for SDE-2 interviews.
>
> **Scope:** Logical design only. No `CREATE TABLE` statements, no application code. We focus on entities, relationships, normalization reasoning, indexing strategy, and scalability concerns.

---

## Table of Contents
1. [How to approach this kind of problem](#1-how-to-approach-this-kind-of-problem)
2. [Step 1 — Identify the entities](#2-step-1--identify-the-entities)
3. [Step 2 — Define the relationships](#3-step-2--define-the-relationships)
4. [Step 3 — Why each table exists](#4-step-3--why-each-table-exists)
5. [Step 4 — The schema in 3NF](#5-step-4--the-schema-in-3nf)
6. [Step 5 — Primary keys and foreign keys](#6-step-5--primary-keys-and-foreign-keys)
7. [Step 6 — Normalization decisions, explained](#7-step-6--normalization-decisions-explained)
8. [Step 7 — Indexing strategy](#8-step-7--indexing-strategy)
9. [Step 8 — ER diagram (text format)](#9-step-8--er-diagram-text-format)
10. [Step 9 — Scalability concerns](#10-step-9--scalability-concerns)
11. [Common interview follow-up questions](#11-common-interview-follow-up-questions)

---

## 1. How to approach this kind of problem

In an interview, never jump straight into tables. The senior interviewer wants to see your **thought process**, in this order:

1. **Clarify requirements** — turn fuzzy English into concrete user actions and business rules.
2. **Identify entities** — nouns from the requirements that have their own identity and lifecycle.
3. **Identify relationships** — verbs that connect entities, plus their cardinality (1:1, 1:N, M:N).
4. **Apply normalization** — eliminate redundancy and update anomalies; usually stop at 3NF unless there is a strong reason.
5. **Choose keys** — natural vs surrogate, composite vs single-column.
6. **Layer in non-functional concerns** — indexes, concurrency, scalability, audit, soft deletes.
7. **Sanity-check with real queries** — "Can I efficiently answer: *show me available Deluxe rooms in Hotel X between June 10–15?*"

We'll follow exactly that order below.

---

## 2. Step 1 — Identify the entities

Reading the requirements carefully, the **nouns** are:

| Candidate | Real entity? | Reason |
|---|---|---|
| User | Yes | Has identity, registers, books, reviews. |
| Hotel | Yes | Has identity, owns rooms, gets reviewed. |
| Room | Yes | Has identity (per hotel), gets booked. |
| RoomType | Yes | "Standard / Deluxe / Suite" is a finite category that may grow (e.g., Presidential). Modeled as its own table so we can attach descriptions, base prices, capacity, etc., and rename without breaking data. |
| Booking | Yes | Has identity (booking number), lifecycle (pending → confirmed → cancelled → completed). |
| Payment | Yes | Has identity (transaction id), independent lifecycle (initiated → success → failed → refunded). One booking may have multiple payment attempts. |
| Review | Yes | Has identity, linked to user + hotel + (often) booking. |
| Booking status, Payment status | Borderline | Could be enums or small lookup tables. We'll discuss the trade-off in §7. |
| Address, City, Country | Borderline | If we only display them, store inline; if we filter/group by them, normalize into a `City` (and `Country`) table. For an interview, mention both options. |

> **Architect's note:** "User" in this domain often splits into **Guest** (the person who stays) and **Account** (the login). For now we'll treat them as one `users` table, but in §11 we discuss separating them.

---

## 3. Step 2 — Define the relationships

State each relationship as a sentence, then label the cardinality:

| Relationship | Cardinality | Notes |
|---|---|---|
| A **User** places many **Bookings**; each Booking belongs to exactly one User. | 1 : N | FK `user_id` on `bookings`. |
| A **Hotel** has many **Rooms**; a Room belongs to exactly one Hotel. | 1 : N | FK `hotel_id` on `rooms`. |
| A **RoomType** classifies many **Rooms**; each Room has exactly one RoomType. | 1 : N | FK `room_type_id` on `rooms`. |
| A **Room** is booked in many **Bookings** (over time); each Booking targets exactly one Room. | 1 : N | FK `room_id` on `bookings`. (See §11 — for multi-room bookings we'd add a junction table.) |
| A **Booking** has one (or a few) **Payments**; each Payment belongs to exactly one Booking. | 1 : N | FK `booking_id` on `payments`. Modeled as 1:N (not 1:1) so we can record retries, partial payments, and refunds as separate rows. |
| A **User** writes many **Reviews**; each Review is written by one User and targets one Hotel. | 1 : N (from each side) | FKs `user_id` and `hotel_id` on `reviews`. |
| (Optional) A **Booking** can have one **Review** (you can only review a hotel you stayed at). | 1 : 0..1 | FK `booking_id` on `reviews`, nullable + UNIQUE. Strongly recommended; prevents fake reviews. |

> **Cardinality cheat sheet for interviews:**
> - **1:1** → usually a sign you should merge tables, *unless* you have a strong reason to split (security, sparse columns, separate lifecycle).
> - **1:N** → FK lives on the "many" side. Most common.
> - **M:N** → requires a **junction (associative) table** with a composite PK.

---

## 4. Step 3 — Why each table exists

Before drawing schemas, justify every table. If you can't justify it, drop it.

| Table | Reason for existence |
|---|---|
| `users` | Authoritative record of a person who can log in and act in the system. Without it we cannot attach bookings or reviews to anyone. |
| `hotels` | The product being sold. Holds name, location, contact, star rating, owner, etc. |
| `room_types` | Categorical attribute of rooms that itself has structure (description, base price, max occupancy). Extracted because (a) the same type appears across many rooms, and (b) we want a single source of truth when the description changes. |
| `rooms` | A bookable inventory unit. Without rooms, hotels are abstract. Each room is one physical asset (e.g., "Room 305"). |
| `bookings` | The core business event. Records "User U reserved Room R from check-in to check-out at price P." Drives revenue, capacity, and analytics. |
| `payments` | Financial record. Split from `bookings` because (a) one booking can have multiple payment events, (b) payment failures should not corrupt the booking row, (c) auditors require an immutable payment log. |
| `reviews` | Post-stay feedback. Linked to a booking to prove the reviewer actually stayed. |
| *(optional)* `cities`, `countries` | Normalize location so we can filter, group, and avoid spelling drift ("Bengaluru" vs "Bangalore"). |
| *(optional)* `booking_statuses`, `payment_statuses` | Lookup tables if statuses carry metadata (label, is_terminal, sort_order); otherwise use a typed enum. |

---

## 5. Step 4 — The schema in 3NF

We'll describe each table in **logical form** — columns, their meaning, and constraints — without SQL syntax. Surrogate `BIGINT` IDs are used throughout for simplicity and to avoid PK changes when business attributes evolve.

### 5.1 `users`
| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `user_id` | BIGINT | PK | Surrogate identifier. |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | Login + contact. |
| `password_hash` | VARCHAR(255) | NOT NULL | Never store plaintext. |
| `full_name` | VARCHAR(150) | NOT NULL | Display. |
| `phone` | VARCHAR(20) | NULL | Optional contact. |
| `created_at`, `updated_at` | TIMESTAMP | NOT NULL | Audit. |
| `is_active` | BOOLEAN | NOT NULL, default TRUE | Soft deactivation. |

### 5.2 `hotels`
| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `hotel_id` | BIGINT | PK | Surrogate. |
| `name` | VARCHAR(200) | NOT NULL | Display. |
| `description` | TEXT | NULL | Marketing copy. |
| `address_line` | VARCHAR(255) | NOT NULL | Street address. |
| `city_id` | BIGINT | FK → `cities.city_id`, NOT NULL | Normalized location. |
| `star_rating` | TINYINT | CHECK (between 1 and 5) | Official rating. |
| `created_at`, `updated_at` | TIMESTAMP | NOT NULL | Audit. |

### 5.3 `room_types`
| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `room_type_id` | BIGINT | PK | Surrogate. |
| `code` | VARCHAR(30) | UNIQUE, NOT NULL | Stable machine code (`STANDARD`, `DELUXE`, `SUITE`). |
| `name` | VARCHAR(50) | NOT NULL | Human label. |
| `description` | TEXT | NULL | What's included. |
| `max_occupancy` | TINYINT | NOT NULL | Capacity. |
| `base_price` | DECIMAL(10,2) | NOT NULL | Reference price; actual price per room can override. |

### 5.4 `rooms`
| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `room_id` | BIGINT | PK | Surrogate. |
| `hotel_id` | BIGINT | FK → `hotels.hotel_id`, NOT NULL | Owning hotel. |
| `room_type_id` | BIGINT | FK → `room_types.room_type_id`, NOT NULL | Category. |
| `room_number` | VARCHAR(10) | NOT NULL | "305", "12B". UNIQUE per hotel. |
| `floor` | SMALLINT | NULL | Optional. |
| `nightly_price` | DECIMAL(10,2) | NOT NULL | Hotel-specific price; falls back to base if equal. |
| `is_active` | BOOLEAN | NOT NULL, default TRUE | Take a room out of inventory without deleting. |
| *Composite UNIQUE* | (`hotel_id`, `room_number`) | | A hotel cannot have two "Room 305". |

### 5.5 `bookings`
| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `booking_id` | BIGINT | PK | Surrogate. |
| `booking_reference` | VARCHAR(20) | UNIQUE, NOT NULL | Human-friendly code (e.g., `BK-2026-000123`). |
| `user_id` | BIGINT | FK → `users.user_id`, NOT NULL | Who booked. |
| `room_id` | BIGINT | FK → `rooms.room_id`, NOT NULL | What was booked. |
| `check_in_date` | DATE | NOT NULL | Inclusive. |
| `check_out_date` | DATE | NOT NULL, CHECK (`check_out_date > check_in_date`) | Exclusive. |
| `nights` | SMALLINT | NOT NULL | **Stored deliberately** for fast aggregation; trade-off discussed in §7. |
| `price_per_night` | DECIMAL(10,2) | NOT NULL | **Snapshot** of price at booking time (prices change!). |
| `total_amount` | DECIMAL(10,2) | NOT NULL | `nights * price_per_night` (+ taxes/fees if modeled). |
| `status` | ENUM/lookup | NOT NULL | `PENDING / CONFIRMED / CANCELLED / COMPLETED / NO_SHOW`. |
| `cancelled_at` | TIMESTAMP | NULL | Set when status moves to CANCELLED. |
| `cancellation_reason` | VARCHAR(255) | NULL | Optional. |
| `created_at`, `updated_at` | TIMESTAMP | NOT NULL | Audit. |

### 5.6 `payments`
| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `payment_id` | BIGINT | PK | Surrogate. |
| `booking_id` | BIGINT | FK → `bookings.booking_id`, NOT NULL | Linked booking. |
| `amount` | DECIMAL(10,2) | NOT NULL | Charged amount (positive for charge, negative for refund — or use a `direction` column). |
| `currency` | CHAR(3) | NOT NULL | ISO-4217 (`INR`, `USD`). |
| `provider` | VARCHAR(30) | NOT NULL | `RAZORPAY`, `STRIPE`, `CASH`. |
| `provider_txn_id` | VARCHAR(100) | UNIQUE | Idempotency: same external txn cannot be recorded twice. |
| `status` | ENUM/lookup | NOT NULL | `INITIATED / SUCCESS / FAILED / REFUNDED`. |
| `paid_at` | TIMESTAMP | NULL | Set on SUCCESS. |
| `created_at`, `updated_at` | TIMESTAMP | NOT NULL | Audit. |

### 5.7 `reviews`
| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `review_id` | BIGINT | PK | Surrogate. |
| `user_id` | BIGINT | FK → `users.user_id`, NOT NULL | Reviewer. |
| `hotel_id` | BIGINT | FK → `hotels.hotel_id`, NOT NULL | Reviewed hotel. |
| `booking_id` | BIGINT | FK → `bookings.booking_id`, UNIQUE, NOT NULL | One review per stay — prevents review spam. |
| `rating` | TINYINT | NOT NULL, CHECK (1..5) | Star rating. |
| `comment` | TEXT | NULL | Free text. |
| `created_at` | TIMESTAMP | NOT NULL | When written. |

### 5.8 Optional supporting tables
- `cities (city_id PK, name, country_id FK)`
- `countries (country_id PK, name, iso_code)`
- `booking_statuses` / `payment_statuses` if statuses need their own attributes.

---

## 6. Step 5 — Primary keys and foreign keys

### Why surrogate PKs everywhere?
- **Stable.** A surrogate `BIGINT` never changes, even when a hotel renames itself or a user changes email.
- **Compact in indexes.** A 8-byte integer is a much cheaper FK than a 255-byte composite natural key.
- **Friendly to ORMs and joins.** Uniform `id` columns simplify code generation.

We still keep **natural unique keys** as `UNIQUE` constraints (email, room_number per hotel, booking_reference, provider_txn_id) to enforce business uniqueness.

### Foreign key summary

| Child table | Column | Parent table | On delete | Notes |
|---|---|---|---|---|
| `hotels` | `city_id` | `cities` | RESTRICT | Don't allow deleting a city that has hotels. |
| `rooms` | `hotel_id` | `hotels` | RESTRICT (or CASCADE if hotel is "soft" deleted only) | A hotel without rooms is fine; rooms without hotel are not. |
| `rooms` | `room_type_id` | `room_types` | RESTRICT | Don't allow deleting a type still in use. |
| `bookings` | `user_id` | `users` | RESTRICT | Keep historical bookings; deactivate users instead. |
| `bookings` | `room_id` | `rooms` | RESTRICT | Same reasoning. |
| `payments` | `booking_id` | `bookings` | RESTRICT | Financial records must outlive UI deletions. |
| `reviews` | `user_id` | `users` | RESTRICT | Preserve review history. |
| `reviews` | `hotel_id` | `hotels` | RESTRICT | Same. |
| `reviews` | `booking_id` | `bookings` | RESTRICT | Same. |

> **Architect's rule:** In an OLTP system handling money or contracts, default to `ON DELETE RESTRICT` and use **soft deletes** (`is_active`, `deleted_at`) at the application level. Cascading deletes are convenient and dangerous.

---

## 7. Step 6 — Normalization decisions, explained

### Quick refresher
- **1NF** — atomic columns, no repeating groups (no `phone1, phone2, phone3`; no comma-separated lists).
- **2NF** — no partial dependencies on a composite key (every non-key column depends on the *whole* key).
- **3NF** — no transitive dependencies (non-key columns depend *only* on the key, not on other non-key columns).

### Decisions made and why

**(a) `room_types` extracted from `rooms`.**
If we had stored `room_type_name` and `room_type_description` directly on `rooms`, the description would be duplicated for every Standard room. Worse, updating the description would require touching thousands of rows — a classic **update anomaly**. Extracting `room_types` removes a transitive dependency `room_id → room_type_name → room_type_description` (violates 3NF).

**(b) `cities` (and `countries`) extracted from `hotels`.**
Storing `city_name`, `country_name` on `hotels` causes:
- Spelling drift ("Bengaluru" vs "Bangalore"),
- Difficult grouping ("hotels per city"),
- Duplicate updates if a city is renamed.
Extracting normalizes the transitive dependency `hotel_id → city_name → country_name`.

**(c) `payments` split from `bookings`.**
A booking can have multiple payment events (initial charge, retry, refund). Putting `payment_status`, `provider`, `provider_txn_id` on `bookings` would force one row per booking and lose retry history — a 1NF/2NF problem the moment a second payment exists.

**(d) `reviews` separate from `bookings`.**
Not every booking yields a review, and a review has its own lifecycle (edits, moderation). Embedding `rating, comment` on `bookings` would create many NULLs and conflate two unrelated concerns.

### Deliberate denormalizations (and why they're OK)

Strict 3NF says **don't store derivable data**. We break that rule in two places, on purpose:

1. **`bookings.nights`** is derivable from `check_out_date - check_in_date`. We store it because:
   - It's read constantly in analytics (`SUM(nights)`, `AVG(nights)`).
   - It's immutable once the dates are fixed.
   - The cost of one extra small column is trivial vs the cost of computing on every query.
2. **`bookings.total_amount`** is derivable from `nights * price_per_night`. Same argument, plus: **prices change**, so storing the snapshot protects historical truth ("how much did this booking cost on the day it was made?").

> **Interview tip:** Always justify denormalization with **read patterns** or **historical integrity**. Never denormalize for laziness.

### Why we don't push to BCNF / 4NF / 5NF here
3NF is sufficient for almost every OLTP design. Going further usually buys little and costs query complexity. Mention this trade-off in interviews — it shows judgment, not ignorance.

---

## 8. Step 7 — Indexing strategy

Indexes are the bridge between "logically correct" and "actually fast." Design them around your **top queries**, not speculatively.

### Indexes you get for free
Every PK is automatically indexed (clustered, on most engines). Every UNIQUE constraint also creates an index.

### Indexes to add explicitly

| Index | Table | Columns | Query it accelerates |
|---|---|---|---|
| `idx_users_email` | `users` | `email` (already UNIQUE) | Login lookup. |
| `idx_rooms_hotel` | `rooms` | `(hotel_id, room_type_id, is_active)` | "Show all active Deluxe rooms in Hotel X." |
| `idx_bookings_room_dates` | `bookings` | `(room_id, check_in_date, check_out_date)` | Availability check: "Is Room R free between dates D1 and D2?" |
| `idx_bookings_user` | `bookings` | `(user_id, created_at DESC)` | "My bookings, newest first." |
| `idx_bookings_status` | `bookings` | `(status, check_in_date)` | "All CONFIRMED bookings checking in today." |
| `idx_payments_booking` | `payments` | `(booking_id, created_at)` | Payment history for a booking. |
| `idx_payments_provider_txn` | `payments` | `provider_txn_id` (UNIQUE) | Idempotency check from webhook. |
| `idx_reviews_hotel` | `reviews` | `(hotel_id, created_at DESC)` | Hotel detail page: "latest reviews." |
| `idx_reviews_hotel_rating` | `reviews` | `(hotel_id, rating)` | Aggregate "avg rating by hotel." |
| `idx_hotels_city` | `hotels` | `(city_id, star_rating)` | "Show 4★+ hotels in Mumbai." |

### Composite index rules of thumb
- **Equality columns first, then range columns.** That's why availability is `(room_id, check_in_date, check_out_date)` — `room_id` is equality, dates are a range.
- **Match the index to ORDER BY** to avoid filesort (`(user_id, created_at DESC)`).
- **Cover the query** when possible — include the small set of columns you `SELECT` so the engine never touches the table.

### Specialized indexes worth mentioning
- **GIN / full-text index** on `hotels.description` and `reviews.comment` for search.
- **GiST / range index** (PostgreSQL `tsrange`) on booking date ranges for efficient overlap detection — this is the "right" answer for availability queries at scale.
- **Partial index** on `WHERE status = 'CONFIRMED'` if most queries filter by it.

### What *not* to index
- Low-cardinality columns alone (`is_active`, `status`) — usually only useful as a *secondary* column in a composite, or as a partial index.
- Every FK by reflex. Index FKs only if you query by them.

---

## 9. Step 8 — ER diagram (text format)

```
                +-------------------+
                |     countries     |
                |-------------------|
                | country_id PK     |
                | name              |
                | iso_code          |
                +---------+---------+
                          |1
                          |
                         N|
                +---------v---------+
                |      cities       |
                |-------------------|
                | city_id PK        |
                | name              |
                | country_id FK     |
                +---------+---------+
                          |1
                          |
                         N|
+-----------+    +--------v--------+      +------------------+
|   users   |    |     hotels      |1    N|     reviews      |
|-----------|    |-----------------|------|------------------|
| user_id PK|    | hotel_id PK     |      | review_id   PK   |
| email UQ  |    | name            |      | user_id     FK   |---+
| pwd_hash  |    | description     |      | hotel_id    FK   |   |
| full_name |    | address_line    |      | booking_id  FK UQ|   |
| phone     |    | city_id     FK  |      | rating (1..5)    |   |
| is_active |    | star_rating     |      | comment          |   |
+-----+-----+    | created_at      |      | created_at       |   |
      |1         +--------+--------+      +---------+--------+   |
      |                   |1                        |1           |
      |                   |                         |            |
      |                  N|                         |            |
      |          +--------v---------+               |            |
      |          |      rooms       |               |            |
      |          |------------------|               |            |
      |          | room_id      PK  |               |            |
      |          | hotel_id     FK  |               |            |
      |          | room_type_id FK  |---+           |            |
      |          | room_number      |   |           |            |
      |          | nightly_price    |   |           |            |
      |          | is_active        |   |           |            |
      |          +--------+---------+   |           |            |
      |                   |1            |           |            |
      |                   |             |N          |            |
      |                  N|     +-------v--------+  |            |
      |                   |     |   room_types   |  |            |
      |                   |     |----------------|  |            |
      |                   |     | room_type_id PK|  |            |
      |                   |     | code        UQ |  |            |
      |                   |     | name           |  |            |
      |                   |     | description    |  |            |
      |                   |     | max_occupancy  |  |            |
      |                   |     | base_price     |  |            |
      |                   |     +----------------+  |            |
      |                   |                         |            |
      |                  N|                         |            |
      |          +--------v---------+               |            |
      |         N|     bookings     |1              |            |
      +----------|------------------|---------------+            |
                 | booking_id   PK  |                            |
                 | booking_ref  UQ  |                            |
                 | user_id      FK  |----------------------------+
                 | room_id      FK  |
                 | check_in_date    |
                 | check_out_date   |
                 | nights           |   (derived, stored)
                 | price_per_night  |   (snapshot)
                 | total_amount     |   (derived, stored)
                 | status           |   PENDING/CONFIRMED/...
                 | cancelled_at     |
                 +--------+---------+
                          |1
                          |
                         N|
                 +--------v---------+
                 |     payments     |
                 |------------------|
                 | payment_id   PK  |
                 | booking_id   FK  |
                 | amount           |
                 | currency         |
                 | provider         |
                 | provider_txn_id UQ |   (idempotency)
                 | status           |   INITIATED/SUCCESS/...
                 | paid_at          |
                 +------------------+
```

**Legend:** `PK` = primary key, `FK` = foreign key, `UQ` = unique. Numbers on edges (`1`, `N`) indicate cardinality on that side.

---

## 10. Step 9 — Scalability concerns

This is where SDE-2 interviews really stress-test you. A correct schema is the floor; the ceiling is whether you can keep it correct, fast, and consistent under load.

### 10.1 Concurrency: the double-booking problem
Two users hitting "Book Room 305 for June 10–15" at the same instant can both succeed if we only check availability with a `SELECT`. Mitigations, in order of preference:

1. **Database-level exclusion constraint** (PostgreSQL `EXCLUDE USING gist` on a `tstzrange`) — the engine itself refuses overlapping rows. Strongest guarantee, no application code can break it.
2. **`SELECT ... FOR UPDATE`** on the room row inside a transaction — serializes booking attempts per room.
3. **Application-level distributed lock** (Redis with TTL) — works across services, but you have to handle the lock dying.
4. **Optimistic concurrency** with a `version` column — cheap, but you must retry on conflict.

> **Interview move:** Don't just say "use a transaction." Name the actual mechanism and discuss its failure mode.

### 10.2 Payment idempotency
Payment gateways retry webhooks. If you naively insert a row per webhook call, you double-charge. The `UNIQUE` constraint on `payments.provider_txn_id` is your safety net. Pair it with idempotency keys at the API layer.

### 10.3 Read-heavy workload
Browsing dwarfs booking by 100:1. Standard patterns:

- **Read replicas** for hotel search, hotel detail, reviews. Send writes (booking, payment) to primary.
- **Caching** hot rows in Redis: hotel listings, room types, top reviews. Invalidate on write.
- **Materialized views / summary tables** for "avg rating per hotel," "rooms available next 30 days." Refresh on schedule or via triggers.

### 10.4 Search
SQL `LIKE '%mumbai%'` doesn't scale. For real search (typo tolerance, faceting, ranking), push hotel data into **Elasticsearch / OpenSearch / Meilisearch** as a denormalized document. The relational DB remains the source of truth; the search index is a read-optimized projection.

### 10.5 Sharding & partitioning
Eventually one server can't hold everything. Options to mention:

- **Vertical partitioning** — split rarely-used wide columns (e.g., `hotels.description`) into a side table.
- **Horizontal partitioning by date** on `bookings` (one partition per month/year) — old bookings become read-only and easy to archive.
- **Sharding by `hotel_id`** for very large multi-tenant systems. Cross-shard queries become hard, so analytics moves to a warehouse.

### 10.6 Archival
Bookings older than N years are rarely queried but legally retained. Move them to a `bookings_archive` table (or cold storage like S3 + Parquet) so the hot table stays small and indexes stay fast.

### 10.7 Auditability and history
Money + contracts demand "who changed what, when." Two common patterns:

- **History tables** (e.g., `bookings_history`) populated by a trigger on update.
- **Append-only event store** for booking lifecycle (`BookingCreated`, `BookingConfirmed`, `BookingCancelled`) — feeds analytics and is naturally auditable.

### 10.8 Time zones
`check_in_date` is a `DATE`, deliberately not a timestamp — hotel check-in is local-civil-time, not an instant. Mixing `TIMESTAMP WITH TIME ZONE` with `DATE` here is a frequent bug.

### 10.9 Money
Use `DECIMAL(p, s)`, never `FLOAT`. Store currency alongside amount. Never assume one currency.

---

## 11. Common interview follow-up questions

A senior interviewer almost always probes these. Have an opinion ready.

1. **"What if a booking can span multiple rooms?"**
   Introduce a `booking_rooms` junction table: `(booking_id, room_id, price_per_night)`. `bookings` then holds shared fields (user, dates, total) and `booking_rooms` holds per-room lines. This is the standard hotel-industry model.

2. **"How do you prevent fake reviews?"**
   Make `reviews.booking_id` `NOT NULL UNIQUE` and require booking `status = COMPLETED`. Optionally mark reviews as `verified_stay = TRUE`.

3. **"Why not store `room_type` as an ENUM column on `rooms`?"**
   Enums require a schema migration to add a value, can't carry attributes (description, capacity, base price), and don't enforce referential integrity if those attributes ever migrate elsewhere. A lookup table is more flexible.

4. **"Where do you store taxes and fees?"**
   Either as a `booking_charges` child table (one row per charge type) or as separate columns on `bookings` if the tax model is simple and fixed. The child-table approach is more flexible and audit-friendly.

5. **"Soft delete or hard delete?"**
   Soft delete (`is_active`, `deleted_at`) for entities referenced by historical records (users, hotels, rooms). Hard delete only for ephemeral data with no downstream references.

6. **"How would you find available rooms for a date range efficiently?"**
   Conceptually: a room is available if **no booking exists** for it whose date range overlaps the requested range and whose status is not `CANCELLED`. With the `(room_id, check_in_date, check_out_date)` index it's a fast index range scan; with a PostgreSQL `tsrange` + GiST index it's a single overlap predicate.

7. **"How would you separate auth from profile?"**
   Split `users` into `accounts` (email, password_hash, mfa) and `profiles` (full_name, phone, preferences) with a 1:1 link. Useful when auth lives in a separate service or when you support multiple identities per profile (Google + email).

---

## TL;DR for the interviewer (and your future self)

- **7 core tables**, 2 optional lookup tables.
- **Surrogate PKs** everywhere; **natural UNIQUEs** preserve business rules.
- **3NF** with two deliberate, justified denormalizations (`nights`, `total_amount`).
- **`payments` split from `bookings`** for lifecycle independence and audit.
- **`reviews.booking_id UNIQUE NOT NULL`** to prevent fake reviews.
- **Composite indexes** built for actual query patterns (availability, user history, hotel listings).
- **Concurrency** handled by a real mechanism (exclusion constraint or `SELECT ... FOR UPDATE`), not vibes.
- **Scalability** discussed in layers: replicas → cache → search index → partition → shard.

Next step (when ready): turn this design into `CREATE TABLE` statements for a specific engine (PostgreSQL recommended for the exclusion constraint and `tsrange` features), then layer in seed data and migration tooling.
