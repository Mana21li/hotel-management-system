-- =============================================================================
-- 02_tables.sql
-- All 9 tables in dependency order. See docs/database-schema-postgres.md §4–§12.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- countries
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS countries (
    country_id   BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    iso_code     CHAR(2)      NOT NULL,

    CONSTRAINT uq_countries_iso_code UNIQUE (iso_code),
    CONSTRAINT uq_countries_name     UNIQUE (name)
);

-- ----------------------------------------------------------------------------
-- cities
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cities (
    city_id      BIGSERIAL    PRIMARY KEY,
    country_id   BIGINT       NOT NULL,
    name         VARCHAR(120) NOT NULL,

    CONSTRAINT fk_cities_country
        FOREIGN KEY (country_id) REFERENCES countries (country_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT uq_cities_country_name UNIQUE (country_id, name)
);

-- ----------------------------------------------------------------------------
-- room_types
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS room_types (
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

-- ----------------------------------------------------------------------------
-- users
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
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

-- ----------------------------------------------------------------------------
-- hotels
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hotels (
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

-- ----------------------------------------------------------------------------
-- rooms
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
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

-- ----------------------------------------------------------------------------
-- bookings
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookings (
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

    -- Prevent overlapping bookings on the same room (needs btree_gist).
    CONSTRAINT no_overlap_booking
        EXCLUDE USING gist (
            room_id WITH =,
            daterange(check_in_date, check_out_date, '[)') WITH &&
        ) WHERE (status IN ('PENDING', 'CONFIRMED', 'COMPLETED'))
);

-- ----------------------------------------------------------------------------
-- payments
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
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

-- ----------------------------------------------------------------------------
-- reviews
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
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
