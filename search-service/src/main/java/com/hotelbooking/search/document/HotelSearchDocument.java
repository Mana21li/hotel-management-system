package com.hotelbooking.search.document;

/**
 * Elasticsearch document shape for the {@code hotels} index.
 * <p>
 * Denormalized read model: combines {@code hotels}, {@code cities}, and the minimum
 * active room price from {@code rooms}. Field names must match {@code es/hotels-index.json}.
 */
public record HotelSearchDocument(
        Long hotelId,
        String name,
        String description,
        String addressLine,
        Long cityId,
        String cityName,
        Integer starRating,
        Double minNightlyPrice,
        boolean active,
        String createdAt,
        String updatedAt
) {
}
