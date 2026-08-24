package com.hotelbooking.controller;

import com.hotelbooking.booking.BookingServiceGateway;
import com.hotelbooking.dto.request.CreateBookingRequest;
import com.hotelbooking.dto.response.BookingResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * HTTP adapter for booking write APIs. Implementation lives in booking-service.
 * Contract: {@code docs/api/bookings.md}
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingServiceGateway bookingServiceGateway;

    public BookingController(BookingServiceGateway bookingServiceGateway) {
        this.bookingServiceGateway = bookingServiceGateway;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            UriComponentsBuilder uriBuilder) {

        BookingResponse created = bookingServiceGateway.createBooking(request);

        URI location = uriBuilder
                .path("/api/bookings/{id}")
                .buildAndExpand(created.bookingId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }
}
