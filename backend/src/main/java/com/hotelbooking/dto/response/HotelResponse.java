package com.hotelbooking.dto.response;

/**
 * API representation of a hotel returned to clients.
 * <p>
 * Intentionally smaller than {@link com.hotelbooking.entity.Hotel}: no {@code cityId},
 * {@code isActive}, or audit timestamps. Those are persistence/internal concerns.
 */
public record HotelResponse(
        Long id,
        String name,
        String description,
        String addressLine,
        Short starRating
) {
}
