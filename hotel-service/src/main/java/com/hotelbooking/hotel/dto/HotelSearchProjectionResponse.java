package com.hotelbooking.hotel.dto;

/** Denormalized hotel row for Elasticsearch projection (search-service consumer). */
public record HotelSearchProjectionResponse(
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
