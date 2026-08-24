package com.hotelbooking.exception;

import java.time.LocalDate;

/**
 * Thrown when a room cannot be booked because an existing booking overlaps the
 * requested dates. Raised when the database {@code EXCLUDE} constraint rejects the
 * insert, guaranteeing correctness even under concurrent requests.
 * <p>
 * Mapped to HTTP 409 (Conflict) by {@link GlobalExceptionHandler}.
 */
public class RoomNotAvailableException extends RuntimeException {

    public RoomNotAvailableException(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        super("Room " + roomId + " is not available for the selected dates ("
                + checkIn + " to " + checkOut + ")");
    }
}
