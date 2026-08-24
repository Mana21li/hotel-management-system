package com.hotelbooking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * API representation of a booking returned to clients.
 * Contract: {@code docs/api/bookings.md}
 * <p>
 * Combines identity ({@code bookingReference}), snapshot/computed money fields
 * ({@code nights}, {@code pricePerNight}, {@code totalAmount}), and friendly
 * context ({@code hotelName}) so the client does not need extra calls or math.
 */
public record BookingResponse(
        Long bookingId,
        String bookingReference,
        Long userId,
        Long roomId,
        String hotelName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer nights,
        BigDecimal pricePerNight,
        BigDecimal totalAmount,
        String status,
        Instant createdAt
) {
}
