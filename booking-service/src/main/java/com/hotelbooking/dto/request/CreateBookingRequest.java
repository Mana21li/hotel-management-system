package com.hotelbooking.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Incoming body for {@code POST /api/bookings}.
 * Contract: {@code docs/api/bookings.md}
 * <p>
 * Field-level (shape) rules live here as Bean Validation annotations. The
 * cross-field rule {@code checkOutDate > checkInDate} is enforced in the service,
 * because a single field annotation cannot compare two fields.
 */
public record CreateBookingRequest(

        @NotNull(message = "userId is required")
        Long userId,

        @NotNull(message = "roomId is required")
        Long roomId,

        @NotNull(message = "checkInDate is required")
        @FutureOrPresent(message = "checkInDate must not be in the past")
        LocalDate checkInDate,

        @NotNull(message = "checkOutDate is required")
        @FutureOrPresent(message = "checkOutDate must not be in the past")
        LocalDate checkOutDate
) {
}
