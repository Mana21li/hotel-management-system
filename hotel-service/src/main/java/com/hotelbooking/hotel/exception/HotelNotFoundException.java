package com.hotelbooking.hotel.exception;

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
