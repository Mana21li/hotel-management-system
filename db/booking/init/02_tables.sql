-- hotel_booking — bookings, payments, reviews, outbox (owned by booking-service).
-- user_id / room_id / hotel_id are logical refs into user-service / hotel_catalog (no FK).

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
    CONSTRAINT chk_bookings_dates          CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_bookings_price          CHECK (price_per_night >= 0),
    CONSTRAINT chk_bookings_total          CHECK (total_amount >= 0),
    CONSTRAINT chk_bookings_cancel_consist CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL)
    ),
    CONSTRAINT no_overlap_booking
        EXCLUDE USING gist (
            room_id WITH =,
            daterange(check_in_date, check_out_date, '[)') WITH &&
        ) WHERE (status IN ('PENDING', 'CONFIRMED', 'COMPLETED'))
);

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
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_payments_provider_txn UNIQUE (provider, provider_txn_id),
    CONSTRAINT chk_payments_amount       CHECK (amount > 0),
    CONSTRAINT chk_payments_currency     CHECK (char_length(currency) = 3),
    CONSTRAINT chk_payments_paid_consist CHECK (
        (status = 'SUCCESS' AND paid_at IS NOT NULL)
        OR (status <> 'SUCCESS' AND paid_at IS NULL)
    )
);

CREATE TABLE IF NOT EXISTS reviews (
    review_id    BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    hotel_id     BIGINT       NOT NULL,
    booking_id   BIGINT       NOT NULL,
    rating       SMALLINT     NOT NULL,
    comment      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_reviews_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (booking_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_reviews_booking UNIQUE (booking_id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);
