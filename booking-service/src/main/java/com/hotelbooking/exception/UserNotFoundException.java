package com.hotelbooking.exception;

/**
 * Thrown when a user does not exist or is inactive.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long userId) {
        super("User not found with id: " + userId);
    }
}
