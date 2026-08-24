package com.hotelbooking.controller;

import com.hotelbooking.dto.response.HotelResponse;
import com.hotelbooking.hotel.HotelServiceGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP adapter for hotel read APIs. Implementation lives in hotel-service.
 * Contract: {@code docs/api/hotels.md}
 */
@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelServiceGateway hotelServiceGateway;

    public HotelController(HotelServiceGateway hotelServiceGateway) {
        this.hotelServiceGateway = hotelServiceGateway;
    }

    @GetMapping
    public List<HotelResponse> listHotels() {
        return hotelServiceGateway.listActiveHotels();
    }

    @GetMapping("/{id}")
    public HotelResponse getHotel(@PathVariable Long id) {
        return hotelServiceGateway.getActiveHotelById(id);
    }
}
