package com.hotelbooking.kafka.event;

import java.time.Instant;

/**
 * Signals that the search read model for a hotel should be refreshed from Postgres.
 * Consumer loads a denormalized document and upserts it into Elasticsearch.
 */
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
