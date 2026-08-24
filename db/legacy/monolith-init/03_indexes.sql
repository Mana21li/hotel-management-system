-- =============================================================================
-- 03_indexes.sql
-- Secondary indexes built around real query patterns.
-- See docs/database-schema-postgres.md §13. PKs and UNIQUE constraints are
-- already indexed automatically, so they are not repeated here.
-- =============================================================================

-- Hotel search / listing.
CREATE INDEX IF NOT EXISTS idx_hotels_city_active_rating
    ON hotels (city_id, is_active, star_rating DESC);

-- Filter rooms within a hotel by type / availability.
CREATE INDEX IF NOT EXISTS idx_rooms_hotel_type_active
    ON rooms (hotel_id, room_type_id, is_active);

-- "My bookings, newest first."
CREATE INDEX IF NOT EXISTS idx_bookings_user_created
    ON bookings (user_id, created_at DESC);

-- Availability check & ops dashboards ("today's check-ins").
CREATE INDEX IF NOT EXISTS idx_bookings_room_dates
    ON bookings (room_id, check_in_date, check_out_date);

-- Only-active-bookings reports — partial index, very small and very fast.
CREATE INDEX IF NOT EXISTS idx_bookings_status_checkin
    ON bookings (status, check_in_date)
    WHERE status IN ('PENDING', 'CONFIRMED');

-- Payment history for a booking, newest first.
CREATE INDEX IF NOT EXISTS idx_payments_booking_created
    ON payments (booking_id, created_at DESC);

-- Hotel detail page reviews, newest first.
CREATE INDEX IF NOT EXISTS idx_reviews_hotel_created
    ON reviews (hotel_id, created_at DESC);

-- For "average rating per hotel" aggregation.
CREATE INDEX IF NOT EXISTS idx_reviews_hotel_rating
    ON reviews (hotel_id, rating);
