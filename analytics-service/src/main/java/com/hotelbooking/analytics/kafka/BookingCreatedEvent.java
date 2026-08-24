package com.hotelbooking.analytics.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Event contract consumed from {@code booking-events}.
 * JSON shape must stay compatible with the booking-service producer/outbox relay.
 */
public record BookingCreatedEvent(
        String eventType,
        Long bookingId,
        String bookingReference,
        Long userId,
        Long roomId,
        String hotelName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer nights,
        BigDecimal totalAmount,
        String status,
        Instant occurredAt
) {
    public static final String TYPE = "BookingCreated";
}
