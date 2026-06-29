package com.hotelbooking.exception;

/**
 * Thrown for cross-field date rule violations that field-level Bean Validation
 * cannot express (e.g. checkOutDate must be strictly after checkInDate).
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class InvalidBookingDateException extends RuntimeException {

    public InvalidBookingDateException(String message) {
        super(message);
    }
}
