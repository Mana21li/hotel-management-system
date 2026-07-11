package com.hotelbooking.exception;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;

import java.io.IOException;
import java.net.ConnectException;

/**
 * Thrown when Elasticsearch is down, unreachable, or the search index is missing.
 * Mapped to HTTP 503 so clients can retry.
 */
public class SearchServiceUnavailableException extends RuntimeException {

    public SearchServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public static SearchServiceUnavailableException from(Throwable cause) {
        if (cause instanceof ElasticsearchException esEx) {
            int status = esEx.response().status();
            if (status == 404) {
                return new SearchServiceUnavailableException(
                        "Search index not found. Run POST /api/admin/search/hotels/reindex", cause);
            }
        }
        if (isConnectionFailure(cause)) {
            return new SearchServiceUnavailableException(
                    "Search service is temporarily unavailable", cause);
        }
        return new SearchServiceUnavailableException("Elasticsearch operation failed", cause);
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
