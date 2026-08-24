package com.hotelbooking.controller;

import com.hotelbooking.dto.request.CreateBookingRequest;
import com.hotelbooking.dto.response.BookingResponse;
import com.hotelbooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * HTTP adapter for booking write APIs.
 * Contract: {@code docs/api/bookings.md}
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * POST /api/bookings — create a PENDING booking.
     * <p>
     * {@code @Valid} runs field-level Bean Validation before this method executes;
     * business validation (room/user existence, availability) happens in the service.
     * Returns 201 Created with a Location header pointing at the new resource.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            UriComponentsBuilder uriBuilder) {

        BookingResponse created = bookingService.createBooking(request);

        URI location = uriBuilder
                .path("/api/bookings/{id}")
                .buildAndExpand(created.bookingId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }
}
