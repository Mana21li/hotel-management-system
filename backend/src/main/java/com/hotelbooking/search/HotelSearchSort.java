package com.hotelbooking.search;

/**
 * Supported sort options for hotel search.
 */
public enum HotelSearchSort {
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    STARS_DESC,
    STARS_ASC;

    public static HotelSearchSort fromParam(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        String normalized = value.trim().toUpperCase().replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid sort '" + value + "'. Allowed: relevance, price_asc, price_desc, stars_desc, stars_asc");
        }
    }
}
