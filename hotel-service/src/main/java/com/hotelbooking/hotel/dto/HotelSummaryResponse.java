package com.hotelbooking.hotel.dto;

/** Minimal hotel summary for cross-service lookups. */
public record HotelSummaryResponse(
        Long hotelId,
        String name,
        boolean active
) {
}
