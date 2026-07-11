package com.hotelbooking.dto.response;

import java.util.List;

/**
 * Response for {@code GET /api/search/hotels}.
 */
public record HotelSearchResponse(
        String query,
        long total,
        int page,
        int size,
        List<HotelSearchHit> hits
) {
}
