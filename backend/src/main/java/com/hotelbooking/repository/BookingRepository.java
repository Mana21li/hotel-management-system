package com.hotelbooking.repository;

import com.hotelbooking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

/**
 * Persistence access for {@link Booking}.
 * <p>
 * Creating a booking uses the inherited {@code save()}. Availability is NOT checked
 * with a separate query here — the database {@code EXCLUDE} constraint rejects
 * overlapping inserts, which the service translates into a 409 Conflict.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Used by concurrency tests to assert exactly one row was inserted for a slot. */
    long countByRoomIdAndCheckInDateAndCheckOutDate(
            Long roomId, LocalDate checkInDate, LocalDate checkOutDate);
}
