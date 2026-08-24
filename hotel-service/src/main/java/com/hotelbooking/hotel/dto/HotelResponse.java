package com.hotelbooking.hotel.dto;

public record HotelResponse(
        Long id,
        String name,
        String description,
        String addressLine,
        Short starRating
) {
}
