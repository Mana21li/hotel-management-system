package com.hotelbooking.dto.response;

/**
 * API representation of a hotel returned to clients.
 * <p>
 * Intentionally smaller than the persistence model: no {@code cityId},
 * {@code isActive}, or audit timestamps. Those stay inside hotel-service.
 */
public record HotelResponse(
        Long id,
        String name,
        String description,
        String addressLine,
        Short starRating
) {
}
