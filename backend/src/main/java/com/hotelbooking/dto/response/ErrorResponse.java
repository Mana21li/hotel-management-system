package com.hotelbooking.dto.response;

import java.time.Instant;

/**
 * Standard error body returned for failed requests.
 * Shape matches the contract in {@code docs/api/hotels.md}.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
