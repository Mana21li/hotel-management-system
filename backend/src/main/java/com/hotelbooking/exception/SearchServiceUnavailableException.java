package com.hotelbooking.exception;

import java.io.IOException;
import java.net.ConnectException;

/**
 * Thrown when the search service (or Elasticsearch behind it) is unavailable.
 * Mapped to HTTP 503 so clients can retry.
 */
public class SearchServiceUnavailableException extends RuntimeException {

    public SearchServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public static SearchServiceUnavailableException from(Throwable cause) {
        if (isConnectionFailure(cause)) {
            return new SearchServiceUnavailableException(
                    "Search service is temporarily unavailable", cause);
        }
        return new SearchServiceUnavailableException("Search operation failed", cause);
    }

    private static boolean isConnectionFailure(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof ConnectException || current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
