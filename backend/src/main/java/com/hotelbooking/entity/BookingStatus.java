package com.hotelbooking.entity;

/**
 * Mirrors the PostgreSQL {@code booking_status} enum type.
 * Values must match the database enum labels exactly.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW
}
