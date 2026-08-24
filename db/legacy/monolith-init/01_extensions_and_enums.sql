-- =============================================================================
-- 01_extensions_and_enums.sql
-- Extensions and ENUM types. Must run before tables.
-- See docs/database-schema-postgres.md §2 for the reasoning behind each choice.
-- =============================================================================

-- btree_gist lets us mix a scalar (room_id WITH =) and a range (daterange WITH &&)
-- in the same EXCLUDE constraint. This is what powers double-booking prevention.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- citext = case-insensitive text. Used for users.email so 'A@x.com' == 'a@x.com'.
CREATE EXTENSION IF NOT EXISTS citext;

-- Booking lifecycle.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_status') THEN
        CREATE TYPE booking_status AS ENUM (
            'PENDING',
            'CONFIRMED',
            'CANCELLED',
            'COMPLETED',
            'NO_SHOW'
        );
    END IF;
END$$;

-- Payment lifecycle.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_status') THEN
        CREATE TYPE payment_status AS ENUM (
            'INITIATED',
            'SUCCESS',
            'FAILED',
            'REFUNDED'
        );
    END IF;
END$$;

-- Payment direction (keeps amount always positive; sign comes from direction).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_direction') THEN
        CREATE TYPE payment_direction AS ENUM (
            'CHARGE',
            'REFUND'
        );
    END IF;
END$$;
