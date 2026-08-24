package com.hotelbooking.search.kafka;

import java.time.Instant;

public record HotelUpsertedEvent(
        String eventType,
        Long hotelId,
        Instant occurredAt
) {
    public static final String TYPE = "HotelUpserted";

    public static HotelUpsertedEvent of(Long hotelId) {
        return new HotelUpsertedEvent(TYPE, hotelId, Instant.now());
    }
}
