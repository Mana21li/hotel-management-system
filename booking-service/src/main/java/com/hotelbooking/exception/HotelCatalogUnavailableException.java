package com.hotelbooking.exception;

public class HotelCatalogUnavailableException extends RuntimeException {

    public HotelCatalogUnavailableException(String message) {
        super(message);
    }

    public HotelCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
