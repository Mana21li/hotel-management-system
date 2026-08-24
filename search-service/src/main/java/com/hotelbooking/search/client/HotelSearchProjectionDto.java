package com.hotelbooking.search.client;

/** JSON from hotel-service {@code GET /internal/catalog/hotels/search-projections}. */
public record HotelSearchProjectionDto(
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
