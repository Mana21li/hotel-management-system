package com.hotelbooking.exception;

import java.io.IOException;
import java.net.ConnectException;

/**
 * Thrown when hotel-service is unreachable.
 * Mapped to HTTP 503 so clients can retry.
 */
public class HotelServiceUnavailableException extends RuntimeException {

    public HotelServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public static HotelServiceUnavailableException from(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof ConnectException || current instanceof IOException) {
                return new HotelServiceUnavailableException(
                        "Hotel catalog is temporarily unavailable", cause);
            }
            current = current.getCause();
        }
        return new HotelServiceUnavailableException("Hotel catalog operation failed", cause);
    }
}
