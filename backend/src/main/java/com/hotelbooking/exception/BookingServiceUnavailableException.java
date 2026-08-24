package com.hotelbooking.exception;

import java.io.IOException;
import java.net.ConnectException;

public class BookingServiceUnavailableException extends RuntimeException {

    public BookingServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public static BookingServiceUnavailableException from(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof ConnectException || current instanceof IOException) {
                return new BookingServiceUnavailableException(
                        "Booking service is temporarily unavailable", cause);
            }
            current = current.getCause();
        }
        return new BookingServiceUnavailableException("Booking operation failed", cause);
    }
}
