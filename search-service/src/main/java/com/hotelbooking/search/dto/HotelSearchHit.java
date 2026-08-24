package com.hotelbooking.search.dto;

/**
 * One hotel returned from Elasticsearch search.
 * <p>
 * Denormalized snapshot from the search index — not loaded from Postgres.
 */
public record HotelSearchHit(
        Long hotelId,
        String name,
        String description,
        String addressLine,
        String cityName,
        Integer starRating,
        Double minNightlyPrice
) {
}
