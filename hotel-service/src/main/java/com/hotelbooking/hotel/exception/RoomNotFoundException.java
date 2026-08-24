package com.hotelbooking.hotel.exception;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(Long roomId) {
        super("Room not found: " + roomId);
    }
}
