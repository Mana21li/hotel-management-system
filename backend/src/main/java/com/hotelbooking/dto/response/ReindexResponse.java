package com.hotelbooking.dto.response;

/**
 * Response for {@code POST /api/admin/search/hotels/reindex}.
 */
public record ReindexResponse(
        int readFromPostgres,
        int indexed,
        int failures,
        String message
) {
}
