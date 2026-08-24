package com.hotelbooking.client;

/** JSON shape from hotel-service {@code GET /internal/catalog/hotels/{id}}. */
public record HotelSummaryDto(
        Long hotelId,
        String name,
        boolean active
) {
}
