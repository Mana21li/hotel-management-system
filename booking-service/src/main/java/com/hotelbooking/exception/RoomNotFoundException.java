package com.hotelbooking.exception;

/**
 * Thrown when a room does not exist or is inactive.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(Long roomId) {
        super("Room not found with id: " + roomId);
    }
}
