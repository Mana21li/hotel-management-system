package com.hotelbooking.search.exception;

/**
 * Thrown when a hotel is not found or is inactive.
 * Mapped to HTTP 404 by {@link SearchExceptionHandler}.
 */
public class HotelNotFoundException extends RuntimeException {

    private final Long hotelId;

    public HotelNotFoundException(Long hotelId) {
        super("Hotel not found with id: " + hotelId);
        this.hotelId = hotelId;
    }

    public Long getHotelId() {
        return hotelId;
    }
}
