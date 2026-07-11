package com.hotelbooking.search;

/**
 * Parsed search request — built from query parameters in the controller.
 */
public record HotelSearchCriteria(
        String query,
        Long cityId,
        Integer minStars,
        Double maxPrice,
        HotelSearchSort sort,
        int page,
        int size
) {
}
