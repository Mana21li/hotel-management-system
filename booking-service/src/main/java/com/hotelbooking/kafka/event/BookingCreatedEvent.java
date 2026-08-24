package com.hotelbooking.kafka.event;

import com.hotelbooking.dto.response.BookingResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain event published after a booking is successfully persisted.
 * <p>
 * This is not an API DTO — it is the contract between the booking write path
 * and downstream consumers (notification, analytics, recommendation).
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

    public static BookingCreatedEvent from(BookingResponse booking) {
        return new BookingCreatedEvent(
                TYPE,
                booking.bookingId(),
                booking.bookingReference(),
                booking.userId(),
                booking.roomId(),
                booking.hotelName(),
                booking.checkInDate(),
                booking.checkOutDate(),
                booking.nights(),
                booking.totalAmount(),
                booking.status(),
                Instant.now()
        );
    }
}
