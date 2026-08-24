CREATE INDEX IF NOT EXISTS idx_hotels_city_active_rating
    ON hotels (city_id, is_active, star_rating DESC);

CREATE INDEX IF NOT EXISTS idx_rooms_hotel_type_active
    ON rooms (hotel_id, room_type_id, is_active);
