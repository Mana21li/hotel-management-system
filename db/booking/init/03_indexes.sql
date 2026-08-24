CREATE INDEX IF NOT EXISTS idx_bookings_user_created
    ON bookings (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bookings_room_dates
    ON bookings (room_id, check_in_date, check_out_date);

CREATE INDEX IF NOT EXISTS idx_bookings_status_checkin
    ON bookings (status, check_in_date)
    WHERE status IN ('PENDING', 'CONFIRMED');

CREATE INDEX IF NOT EXISTS idx_payments_booking_created
    ON payments (booking_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reviews_hotel_created
    ON reviews (hotel_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reviews_hotel_rating
    ON reviews (hotel_id, rating);
