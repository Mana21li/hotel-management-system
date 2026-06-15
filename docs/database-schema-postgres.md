# Hotel Booking System — PostgreSQL Schema (DDL + Reasoning)

> Companion to [`database-design.md`](./database-design.md). Same 3NF design, now expressed as concrete `CREATE TABLE` statements with mentor-style explanation of every choice — engine, types, keys, constraints, ENUMs, defaults, and indexes.

---

## Table of Contents
1. [Why PostgreSQL over MySQL for this project](#1-why-postgresql-over-mysql-for-this-project)
2. [Setup — extensions and ENUM types](#2-setup--extensions-and-enum-types)
3. [Migration order (and why)](#3-migration-order-and-why)
4. [`countries`](#4-countries)
5. [`cities`](#5-cities)
6. [`room_types`](#6-room_types)
7. [`users`](#7-users)
8. [`hotels`](#8-hotels)
9. [`rooms`](#9-rooms)
10. [`bookings`](#10-bookings)
11. [`payments`](#11-payments)
12. [`reviews`](#12-reviews)
13. [Indexes — built around real query patterns](#13-indexes--built-around-real-query-patterns)
14. [Auto-update of `updated_at`](#14-auto-update-of-updated_at)
15. [Mentor's recap](#15-mentors-recap)

---

## 1. Why PostgreSQL over MySQL for this project

Both are excellent engines; the **right** answer in interviews is *"it depends — here's how I'd choose."* For a **hotel booking system**, PostgreSQL wins on five concrete points that matter directly to our domain:

| Concern in our domain | PostgreSQL | MySQL (InnoDB) |
|---|---|---|
| Preventing **double-booking** of the same room for overlapping dates | `EXCLUDE USING gist` with `daterange` — the engine itself rejects overlapping rows. **This is decisive.** | No equivalent. You must rely on `SELECT ... FOR UPDATE` + application logic, which is correct but fragile. |
| **`CHECK` constraints** (e.g., `check_out > check_in`, `rating BETWEEN 1 AND 5`) | Enforced rigorously since forever. | Only properly enforced from **MySQL 8.0.16**; older versions silently ignored them. |
| **ENUM types** | First-class, alterable, reusable across columns. | Per-column; harder to keep consistent. |
| **Range types** (`daterange`, `tsrange`) for availability queries | Native, with `&&` (overlap) and `@>` (contains) operators. | Must be simulated with two `DATE` columns + manual predicates. |
| **Partial indexes** (e.g., index only `WHERE status = 'CONFIRMED'`) | Native. | Not supported (you must hack with generated columns). |

Honorable mentions: `RETURNING` for single-roundtrip writes, mature `JSONB`, `CITEXT` for case-insensitive text, and excellent CTE / window-function support. MySQL also has fine features (clustered PKs, online DDL, more hosted options), but for a **transactional booking domain** the constraint story alone tips the scale.

> **Conclusion:** We design for **PostgreSQL 14+**. Any version since 9.6 supports the core features; 14+ buys us better CTE optimization and stable `gist` defaults.

---

## 2. Setup — extensions and ENUM types

Before any table, we install the extensions and create the enum types we'll reference.

```sql
-- Needed so we can put `room_id` (a scalar) and a range in the same exclusion constraint.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Optional: case-insensitive email matching ("Alice@x.com" == "alice@x.com").
CREATE EXTENSION IF NOT EXISTS citext;

-- ENUM: booking lifecycle.
CREATE TYPE booking_status AS ENUM (
    'PENDING',
    'CONFIRMED',
    'CANCELLED',
    'COMPLETED',
    'NO_SHOW'
);

-- ENUM: payment lifecycle.
CREATE TYPE payment_status AS ENUM (
    'INITIATED',
    'SUCCESS',
    'FAILED',
    'REFUNDED'
);

-- ENUM: payment direction (charge vs refund) — keeps `amount` always positive.
CREATE TYPE payment_direction AS ENUM (
    'CHARGE',
    'REFUND'
);
```

**Why each choice?**

- **`btree_gist` extension** — without it you cannot mix a scalar (`room_id` with `=`) and a range (`daterange` with `&&`) in one `EXCLUDE` constraint. This is the *whole* mechanism that makes overlap prevention work, so we install it upfront.
- **`citext`** — emails are case-insensitive in practice. Using `CITEXT` for `users.email` lets `UNIQUE` and equality lookups Just Work without `LOWER()` everywhere.
- **`booking_status` / `payment_status`** — using ENUMs (vs `VARCHAR` + `CHECK IN (...)` or a lookup table) is appropriate when:
  - The set is **small, stable, and code-controlled** (your application has a switch statement over these values),
  - You want **type safety**: an invalid status fails at insert time, not at deploy time,
  - You don't need to attach extra columns to each value (if you do, switch to a lookup table).
- **`payment_direction`** — keeping `amount` non-negative and direction explicit prevents subtle bugs when summing financial rows. `SUM(amount) FILTER (WHERE direction = 'CHARGE')` reads naturally; a sign-based scheme is error-prone.
- We did **not** ENUM the `payment_provider` (Stripe, Razorpay, Cash…). That list churns; a `VARCHAR(30)` keeps onboarding new providers a single-row change instead of a migration.

> **Mentor tip:** The "ENUM vs lookup table" question is a classic interview prompt. The rule of thumb: ENUM if values are *behavioral* (the application branches on them); lookup table if values are *data* (they have their own attributes the UI displays).

---

## 3. Migration order (and why)

Tables must be created in dependency order so foreign keys can resolve. Our order:

1. `countries`
2. `cities`            *(FK → countries)*
3. `room_types`        *(no FK)*
4. `users`             *(no FK)*
5. `hotels`            *(FK → cities)*
6. `rooms`             *(FK → hotels, room_types)*
7. `bookings`          *(FK → users, rooms)*
8. `payments`          *(FK → bookings)*
9. `reviews`           *(FK → users, hotels, bookings)*

In a real codebase this becomes 9 numbered migration files (`0001_create_countries.sql`, etc.), one per table, so they can be rolled back independently and reordered in code review.

---

## 4. `countries`

```sql
CREATE TABLE countries (
    country_id   BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    iso_code     CHAR(2)      NOT NULL,

    CONSTRAINT uq_countries_iso_code UNIQUE (iso_code),
    CONSTRAINT uq_countries_name     UNIQUE (name)
);
```

**Purpose:** Normalized list of countries so we don't store the string "India" thousands of times across hotels (and so a typo can't create a parallel "Inida").

**Columns:**

| Column | Why it exists |
|---|---|
| `country_id` (`BIGSERIAL` PK) | Surrogate, auto-incrementing 8-byte integer. Stable even if the country renames itself. `BIGSERIAL` is the legacy form of `GENERATED ALWAYS AS IDENTITY` — both work; `BIGSERIAL` reads cleaner in tutorials. |
| `name` (`VARCHAR(100)`) | Human-readable name. Length cap is defensive — no country name approaches 100 chars. |
| `iso_code` (`CHAR(2)`) | The **stable, machine-readable** identifier (ISO 3166-1 alpha-2: `IN`, `US`, `GB`). `CHAR(2)` because the length is fixed and storage is predictable. |

**Constraints:**

- `UNIQUE (iso_code)` — the ISO code is the natural key; two rows with `IN` would be a data integrity bug.
- `UNIQUE (name)` — defensive; prevents two "United States" rows differing by trailing whitespace.

**Relationships:** Parent of `cities (country_id)`.

---

## 5. `cities`

```sql
CREATE TABLE cities (
    city_id      BIGSERIAL    PRIMARY KEY,
    country_id   BIGINT       NOT NULL,
    name         VARCHAR(120) NOT NULL,

    CONSTRAINT fk_cities_country
        FOREIGN KEY (country_id) REFERENCES countries (country_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT uq_cities_country_name UNIQUE (country_id, name)
);
```

**Purpose:** Hotels reference a `city_id`, not a free-text city name. This eliminates spelling drift ("Bengaluru" vs "Bangalore") and enables clean grouping ("hotels per city").

**Columns:**

| Column | Why it exists |
|---|---|
| `city_id` | Surrogate PK. |
| `country_id` | The country the city belongs to. Composite uniqueness — "Springfield" exists in many countries. |
| `name` | Display name. |

**Constraints:**

- **FK → `countries`** with `ON UPDATE CASCADE` (countries almost never get renumbered, but if they did we want it to propagate) and `ON DELETE RESTRICT` (you shouldn't be able to nuke a country while cities still depend on it).
- `UNIQUE (country_id, name)` — composite uniqueness allows "Springfield" in both US and Canada, but not twice in one country.

**Relationships:** Child of `countries`; parent of `hotels (city_id)`.

---

## 6. `room_types`

```sql
CREATE TABLE room_types (
    room_type_id    BIGSERIAL      PRIMARY KEY,
    code            VARCHAR(30)    NOT NULL,
    name            VARCHAR(50)    NOT NULL,
    description     TEXT,
    max_occupancy   SMALLINT       NOT NULL,
    base_price      NUMERIC(10, 2) NOT NULL,

    CONSTRAINT uq_room_types_code        UNIQUE (code),
    CONSTRAINT chk_room_types_occupancy  CHECK (max_occupancy BETWEEN 1 AND 10),
    CONSTRAINT chk_room_types_base_price CHECK (base_price >= 0)
);
```

**Purpose:** Catalog of bookable room categories. Why a table and not an ENUM? Because each type **carries attributes** (description, capacity, base price) — that's data, not behavior. New types (e.g., "Family Suite") should be one `INSERT`, not a schema migration.

**Columns:**

| Column | Why it exists |
|---|---|
| `room_type_id` | Surrogate PK. |
| `code` (`VARCHAR(30)`, UNIQUE) | Machine-stable identifier (`STANDARD`, `DELUXE`, `SUITE`). The UI and code can switch on `code`; the `name` is free to be rewritten by marketing without breaking anything. |
| `name` | Human-friendly label. |
| `description` | Marketing copy. Nullable — early on you might not have one. |
| `max_occupancy` (`SMALLINT`) | Capacity per room of this type. Drives availability search ("rooms for 4 guests"). |
| `base_price` (`NUMERIC(10, 2)`) | Reference price. The `nightly_price` on each room can override; this is the default. **Always `NUMERIC` / `DECIMAL` for money — never `FLOAT` or `REAL`.** |

**Constraints:**

- `CHECK (max_occupancy BETWEEN 1 AND 10)` — sanity bound. Catches typos like `100` early.
- `CHECK (base_price >= 0)` — negative prices are nonsense.

**Relationships:** Parent of `rooms (room_type_id)`.

---

## 7. `users`

```sql
CREATE TABLE users (
    user_id         BIGSERIAL      PRIMARY KEY,
    email           CITEXT         NOT NULL,
    password_hash   VARCHAR(255)   NOT NULL,
    full_name       VARCHAR(150)   NOT NULL,
    phone           VARCHAR(20),
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email      UNIQUE (email),
    CONSTRAINT chk_users_email_fmt CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);
```

**Purpose:** Authoritative record of a person who can log in and act in the system (book rooms, write reviews).

**Columns:**

| Column | Why it exists |
|---|---|
| `user_id` | Surrogate PK. Never expose this externally if you don't have to; use a UUID column for public IDs in real systems. |
| `email` (`CITEXT`) | Login identifier + contact. `CITEXT` makes `'Alice@x.com'` and `'alice@x.com'` equal under `=` and `UNIQUE` — no more `LOWER(email)` everywhere. |
| `password_hash` (`VARCHAR(255)`) | **Never** store plaintext. 255 is wide enough for any modern hash (bcrypt, argon2id). The application is responsible for hashing; the DB just stores opaque bytes. |
| `full_name` | Display. Combined name field is fine for v1; in a real product you'd split into `first/last` only if you genuinely need it. |
| `phone` | Nullable contact. `VARCHAR(20)` covers E.164 international numbers (`+91 98765 43210`). For real telephony, store the canonical E.164 form (`+919876543210`) and validate format. |
| `is_active` | Soft-deactivation flag. We never hard-delete users referenced by bookings/payments (financial history must survive). |
| `created_at`, `updated_at` (`TIMESTAMPTZ`) | Audit. **Always use `TIMESTAMPTZ`** in PostgreSQL for instants — it stores UTC and converts on read, eliminating an entire category of timezone bugs. `now()` is the default. |

**Constraints:**

- `UNIQUE (email)` — one account per email.
- `CHECK (email ~* '...')` — cheap defensive regex. Application-level validation should also exist; this is a last line of defense.

**Relationships:** Parent of `bookings (user_id)` and `reviews (user_id)`.

---

## 8. `hotels`

```sql
CREATE TABLE hotels (
    hotel_id      BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    address_line  VARCHAR(255) NOT NULL,
    city_id       BIGINT       NOT NULL,
    star_rating   SMALLINT     NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_hotels_city
        FOREIGN KEY (city_id) REFERENCES cities (city_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_hotels_star_rating CHECK (star_rating BETWEEN 1 AND 5)
);
```

**Purpose:** The product being sold. Holds metadata customers see in search/listing/detail pages.

**Columns:**

| Column | Why it exists |
|---|---|
| `hotel_id` | Surrogate PK. |
| `name` | Display name. |
| `description` | Long marketing copy → `TEXT` (unbounded). |
| `address_line` | Street address (single line is fine for v1; production may want `address_line_1`, `address_line_2`, `postal_code`). |
| `city_id` | **Normalized** location. Joined to `cities` (and through to `countries`) for filtering. |
| `star_rating` (`SMALLINT`) | Official 1–5 rating set by hotel chains, distinct from user reviews. |
| `is_active` | Soft-deactivation so we don't break historical bookings when a hotel closes. |
| `created_at`, `updated_at` | Audit. |

**Constraints:**

- FK to `cities` with the same `CASCADE/RESTRICT` pair we used for `cities → countries`.
- `CHECK (star_rating BETWEEN 1 AND 5)` — guard against `0` or `7`.

**Relationships:**

- Child of `cities`.
- Parent of `rooms (hotel_id)` and `reviews (hotel_id)`.

---

## 9. `rooms`

```sql
CREATE TABLE rooms (
    room_id        BIGSERIAL      PRIMARY KEY,
    hotel_id       BIGINT         NOT NULL,
    room_type_id   BIGINT         NOT NULL,
    room_number    VARCHAR(10)    NOT NULL,
    floor          SMALLINT,
    nightly_price  NUMERIC(10, 2) NOT NULL,
    is_active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT fk_rooms_hotel
        FOREIGN KEY (hotel_id) REFERENCES hotels (hotel_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_rooms_type
        FOREIGN KEY (room_type_id) REFERENCES room_types (room_type_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT uq_rooms_hotel_number    UNIQUE (hotel_id, room_number),
    CONSTRAINT chk_rooms_nightly_price  CHECK (nightly_price >= 0)
);
```

**Purpose:** A physical, bookable inventory unit. One row = one room in one hotel.

**Columns:**

| Column | Why it exists |
|---|---|
| `room_id` | Surrogate PK. |
| `hotel_id` | Owning hotel. |
| `room_type_id` | Category (drives capacity, default price, description). |
| `room_number` (`VARCHAR(10)`) | The hotel-facing label ("305", "12B", "PH-01"). `VARCHAR` because room numbers aren't strictly numeric. |
| `floor` (`SMALLINT`, nullable) | Optional; useful for accessibility filters ("ground floor only"). |
| `nightly_price` | Per-room override of `room_types.base_price`. Lets the front desk price a corner Deluxe higher than a standard Deluxe. |
| `is_active` | Take a room out of inventory (renovation, permanently closed) without deleting historical bookings. |
| `created_at`, `updated_at` | Audit. |

**Constraints:**

- FK to `hotels` and `room_types`, both `RESTRICT` on delete.
- `UNIQUE (hotel_id, room_number)` — a hotel cannot have two "Room 305". Composite uniqueness, **not** global — the same room number obviously appears in many hotels.
- `CHECK (nightly_price >= 0)` — no negative prices.

**Relationships:** Child of `hotels` and `room_types`; parent of `bookings (room_id)`.

---

## 10. `bookings`

This is the heart of the system. The schema must guarantee three things:

1. **No double-booking** of the same room for overlapping dates.
2. **Check-out is strictly after check-in.**
3. **Price at booking time is preserved**, even if the room's `nightly_price` later changes.

```sql
CREATE TABLE bookings (
    booking_id           BIGSERIAL      PRIMARY KEY,
    booking_reference    VARCHAR(20)    NOT NULL,
    user_id              BIGINT         NOT NULL,
    room_id              BIGINT         NOT NULL,
    check_in_date        DATE           NOT NULL,
    check_out_date       DATE           NOT NULL,
    nights               SMALLINT       NOT NULL
                         GENERATED ALWAYS AS (check_out_date - check_in_date) STORED,
    price_per_night      NUMERIC(10, 2) NOT NULL,
    total_amount         NUMERIC(12, 2) NOT NULL,
    status               booking_status NOT NULL DEFAULT 'PENDING',
    cancelled_at         TIMESTAMPTZ,
    cancellation_reason  VARCHAR(255),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT uq_bookings_reference UNIQUE (booking_reference),

    CONSTRAINT fk_bookings_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_bookings_room
        FOREIGN KEY (room_id) REFERENCES rooms (room_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_bookings_dates           CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_bookings_price           CHECK (price_per_night >= 0),
    CONSTRAINT chk_bookings_total           CHECK (total_amount    >= 0),
    CONSTRAINT chk_bookings_cancel_consist  CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL)
    ),

    -- The killer feature: prevent overlapping bookings on the same room.
    -- Requires the btree_gist extension (loaded in setup).
    CONSTRAINT no_overlap_booking
        EXCLUDE USING gist (
            room_id WITH =,
            daterange(check_in_date, check_out_date, '[)') WITH &&
        ) WHERE (status IN ('PENDING', 'CONFIRMED', 'COMPLETED'))
);
```

**Purpose:** The core business event. Captures *who* booked *what* room for *which* dates at *what* price, plus where the booking is in its lifecycle.

**Columns:**

| Column | Why it exists |
|---|---|
| `booking_id` | Surrogate PK for joins. |
| `booking_reference` (`VARCHAR(20)`, UNIQUE) | Human-friendly code (`BK-2026-000123`) shown to customers and support. Distinct from the internal PK. |
| `user_id` | Who booked. |
| `room_id` | What was booked. For v1 we keep one room per booking; for multi-room reservations see the follow-ups in the design doc. |
| `check_in_date` (`DATE`) | Inclusive. **`DATE`, not `TIMESTAMPTZ`** — check-in is a local-civil-time concept; using a timestamp here invites timezone bugs. |
| `check_out_date` (`DATE`) | Exclusive (matches the `[)` interval below). |
| `nights` (generated, stored) | `check_out_date - check_in_date`. We store it (computed by the DB, not the app) so reporting queries don't have to recompute, and the value can never disagree with the dates. |
| `price_per_night` | **Snapshot** of price at booking time. The room's price may change tomorrow; the customer's contract was at *this* price. |
| `total_amount` (`NUMERIC(12, 2)`) | `nights * price_per_night` (+ taxes/fees in a richer schema). Wider precision (`12,2`) than per-night to absorb long stays. |
| `status` (`booking_status` ENUM) | Lifecycle. `PENDING` → `CONFIRMED` → `COMPLETED`, or → `CANCELLED` / `NO_SHOW`. |
| `cancelled_at`, `cancellation_reason` | Set together when status moves to `CANCELLED`. Enforced by the consistency check below. |
| `created_at`, `updated_at` | Audit. |

**Constraints — the interesting part:**

- **`CHECK (check_out_date > check_in_date)`** — a zero-night or backwards booking is impossible at the DB layer.
- **`CHECK (...cancellation consistency...)`** — `cancelled_at` is set if and only if the row is `CANCELLED`. Prevents the "looks cancelled but no timestamp" data quality issue.
- **`EXCLUDE USING gist (...)`** — the showstopper. In plain English:
  > "Reject any new or updated row whose `room_id` equals an existing row's `room_id` AND whose `[check_in_date, check_out_date)` range overlaps the existing row's range, **as long as the existing row is in an active status** (PENDING/CONFIRMED/COMPLETED)."
  - `daterange(check_in, check_out, '[)')` builds a half-open range: check-in inclusive, check-out exclusive — exactly matching hotel semantics (a guest leaves the morning of check-out; that night is free).
  - The `WHERE (status IN (...))` clause is a **partial exclusion** — cancelled bookings don't block new ones for the same dates. That's what you want.
  - Inserting a conflicting row raises a clear database error; no application-level race condition.

> **Mentor moment:** This single constraint replaces *hundreds of lines* of careful application code that would otherwise be needed (and would still be wrong under concurrency). It's the single biggest reason we chose PostgreSQL.

**Relationships:**

- Child of `users` and `rooms`.
- Parent of `payments (booking_id)` and (optionally) `reviews (booking_id)`.

---

## 11. `payments`

```sql
CREATE TABLE payments (
    payment_id        BIGSERIAL          PRIMARY KEY,
    booking_id        BIGINT             NOT NULL,
    direction         payment_direction  NOT NULL DEFAULT 'CHARGE',
    amount            NUMERIC(12, 2)     NOT NULL,
    currency          CHAR(3)            NOT NULL DEFAULT 'INR',
    provider          VARCHAR(30)        NOT NULL,
    provider_txn_id   VARCHAR(100),
    status            payment_status     NOT NULL DEFAULT 'INITIATED',
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ        NOT NULL DEFAULT now(),

    CONSTRAINT fk_payments_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (booking_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT uq_payments_provider_txn UNIQUE (provider, provider_txn_id),

    CONSTRAINT chk_payments_amount         CHECK (amount > 0),
    CONSTRAINT chk_payments_currency       CHECK (char_length(currency) = 3),
    CONSTRAINT chk_payments_paid_consist   CHECK (
        (status = 'SUCCESS' AND paid_at IS NOT NULL)
        OR (status <> 'SUCCESS' AND paid_at IS NULL)
    )
);
```

**Purpose:** Append-only-ish ledger of all payment events tied to a booking. **One booking → many payments** because:

- Payment may be retried after a failure,
- A refund is a separate row (with `direction = 'REFUND'`),
- Auditors want every gateway interaction visible.

**Columns:**

| Column | Why it exists |
|---|---|
| `payment_id` | Surrogate PK. |
| `booking_id` | Which booking this payment relates to. |
| `direction` (`payment_direction` ENUM) | `CHARGE` or `REFUND`. Keeps `amount` non-negative and aggregation crystal-clear. |
| `amount` (`NUMERIC(12, 2)`) | The money moved. Always positive; sign comes from `direction`. |
| `currency` (`CHAR(3)`) | ISO 4217 (`INR`, `USD`). Never hardcode currency in application code — multi-currency creeps in faster than you expect. |
| `provider` (`VARCHAR(30)`) | Which gateway (`STRIPE`, `RAZORPAY`, `CASH`). Plain VARCHAR because the set churns. |
| `provider_txn_id` | The gateway's own ID for this transaction. **The idempotency key.** |
| `status` (`payment_status` ENUM) | Lifecycle. |
| `paid_at` | Set when `status` transitions to `SUCCESS`. |
| `created_at`, `updated_at` | Audit. |

**Constraints — the interesting part:**

- **`UNIQUE (provider, provider_txn_id)`** — payment gateways retry webhooks. Without this, a flaky network causes you to record the *same* charge twice, which means you bill the customer twice. The unique constraint makes the second insert fail loudly. This is **idempotency at the database layer**, and it's the right place to put it.
- **`CHECK (amount > 0)`** — combined with `direction`, makes data unambiguous.
- **Paid-consistency check** — `paid_at` is set iff `status = 'SUCCESS'`. Same pattern as bookings/cancellation.

**Relationships:** Child of `bookings`.

---

## 12. `reviews`

```sql
CREATE TABLE reviews (
    review_id    BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    hotel_id     BIGINT       NOT NULL,
    booking_id   BIGINT       NOT NULL,
    rating       SMALLINT     NOT NULL,
    comment      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_reviews_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_reviews_hotel
        FOREIGN KEY (hotel_id) REFERENCES hotels (hotel_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_reviews_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (booking_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT uq_reviews_booking UNIQUE (booking_id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);
```

**Purpose:** Post-stay feedback. Always tied to a real booking, which is how we prevent fake/spam reviews.

**Columns:**

| Column | Why it exists |
|---|---|
| `review_id` | Surrogate PK. |
| `user_id` | The reviewer. (Yes, derivable from `booking_id → bookings.user_id`, but storing it is a small denormalization that makes "all reviews by user X" queries cheap.) |
| `hotel_id` | The hotel being reviewed. (Same justification — derivable from the booking but very frequently queried directly.) |
| `booking_id` | The stay this review is about. |
| `rating` (`SMALLINT`) | 1–5 stars. |
| `comment` (`TEXT`) | Free-form. Optional. |
| `created_at` | When the review was written. |

**Constraints — the interesting part:**

- **`UNIQUE (booking_id)`** — at most one review per stay. Combined with the `NOT NULL`, this means **you cannot review without a booking** *and* **you cannot review the same booking twice**. This single constraint kills two abuse vectors at once.
- **`CHECK (rating BETWEEN 1 AND 5)`** — guards against `0` or `42` stars.

**Application-level rule (not a DB constraint):** Only allow review creation when the linked booking's `status = 'COMPLETED'`. Easy to enforce in the service layer; expressible as a DB trigger if you want belt-and-braces.

> **Mentor moment:** Storing `user_id` and `hotel_id` on `reviews` even though they're derivable from `booking_id` is a deliberate denormalization — same family as `bookings.nights` and `bookings.total_amount`. The justification is **read patterns**: "show me all my reviews" and "show me all reviews for this hotel" are core queries that shouldn't have to join through `bookings` every time. Document the redundancy and enforce it in the service layer (or via a trigger) so it can't drift.

**Relationships:** Child of `users`, `hotels`, and `bookings`.

---

## 13. Indexes — built around real query patterns

PKs and UNIQUE constraints already give you indexes for free. Add these explicitly to support the queries your application actually runs.

```sql
-- Hotel search / listing.
CREATE INDEX idx_hotels_city_active_rating
    ON hotels (city_id, is_active, star_rating DESC);

-- Filter rooms within a hotel by type / availability.
CREATE INDEX idx_rooms_hotel_type_active
    ON rooms (hotel_id, room_type_id, is_active);

-- "My bookings, newest first."
CREATE INDEX idx_bookings_user_created
    ON bookings (user_id, created_at DESC);

-- Availability check & ops dashboards ("today's check-ins").
CREATE INDEX idx_bookings_room_dates
    ON bookings (room_id, check_in_date, check_out_date);

-- Only-active-bookings reports — partial index, very small and very fast.
CREATE INDEX idx_bookings_status_checkin
    ON bookings (status, check_in_date)
    WHERE status IN ('PENDING', 'CONFIRMED');

-- Payment history for a booking, newest first.
CREATE INDEX idx_payments_booking_created
    ON payments (booking_id, created_at DESC);

-- Hotel detail page reviews, newest first.
CREATE INDEX idx_reviews_hotel_created
    ON reviews (hotel_id, created_at DESC);

-- For "average rating per hotel" aggregation.
CREATE INDEX idx_reviews_hotel_rating
    ON reviews (hotel_id, rating);
```

**Why each one:**

- **Composite order matters.** `idx_bookings_room_dates` puts `room_id` first because the room is always an equality filter; the dates are a range. Range columns go *after* equality columns in a B-tree index — otherwise the range scan can't restart efficiently for each room.
- **Partial indexes** (`WHERE status IN (...)`) are a PostgreSQL superpower. The `idx_bookings_status_checkin` index only contains the small slice of bookings that aren't terminal — it's a fraction of the size and stays hot in cache.
- **`DESC` on `created_at`** matches the usual "newest first" `ORDER BY`, avoiding a sort step.
- We did **not** add an index on every FK by reflex. We added indexes only where a real query justifies them.

> **Mentor moment:** Indexing is iterative. Ship the schema, observe `EXPLAIN ANALYZE` on your slowest queries, then add or refactor indexes. Premature indexing slows down writes for no read benefit.

---

## 14. Auto-update of `updated_at`

PostgreSQL doesn't auto-update `updated_at` for you (MySQL has `ON UPDATE CURRENT_TIMESTAMP`; PostgreSQL chose to make you do it explicitly so the behavior is visible). One trigger function, reused across tables:

```sql
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_hotels_updated_at
    BEFORE UPDATE ON hotels
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_rooms_updated_at
    BEFORE UPDATE ON rooms
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_bookings_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

**Why a trigger and not the application?** Because *every* writer (your app, a DBA `UPDATE`, a migration script, a future microservice) gets correct behavior for free. The DB is the single point of truth for this housekeeping field.

---

## 15. Mentor's recap

Step back from the SQL and notice the *patterns* that repeat:

1. **Surrogate `BIGSERIAL` PK everywhere.** Stable, compact, ORM-friendly. Natural keys live in `UNIQUE` constraints.
2. **`NUMERIC(p, s)` for money. Never `FLOAT`.** Period.
3. **`TIMESTAMPTZ` for instants, `DATE` for local-civil-day concepts.** Don't mix them.
4. **ENUMs for *behavior*, lookup tables for *data*, plain `VARCHAR` for *churning* sets.** Three distinct rules, three distinct uses.
5. **Foreign keys default to `ON DELETE RESTRICT`** + soft-delete flags. Cascading deletes are too dangerous in a domain with money and contracts.
6. **CHECK constraints make wrong data impossible** (date ordering, rating range, cancellation consistency, paid-at consistency, positive amounts). Each one is a bug you'll never have to debug.
7. **One special constraint earns its keep above all others:**
   ```sql
   EXCLUDE USING gist (
       room_id WITH =,
       daterange(check_in_date, check_out_date, '[)') WITH &&
   ) WHERE (status IN ('PENDING', 'CONFIRMED', 'COMPLETED'))
   ```
   It single-handedly eliminates double-booking races. **Memorize this pattern for interviews** — being able to write it (and explain *why* each piece is there) marks you as someone who has actually built booking systems, not just read about them.
8. **Idempotency lives at the DB:** `UNIQUE (provider, provider_txn_id)` on `payments` is the single most important defense against double-charging customers when webhooks retry.
9. **One trigger replaces five `updated_at` bugs.** Put housekeeping logic in the database when it's universal.
10. **Indexes are designed against queries**, not added speculatively. Equality columns first, range columns second, `ORDER BY` direction matched, partial indexes used aggressively.

When you can defend each of these choices verbally, in 30 seconds each, with one concrete example, you're operating at SDE-2 level on database design.

**Next step (when ready):** turn this DDL into actual migration files (`flyway` / `liquibase` / `node-pg-migrate` / `alembic`), seed a small dataset, and start writing the queries that will drive the API (`search available rooms`, `confirm booking`, `record payment`, `submit review`).
