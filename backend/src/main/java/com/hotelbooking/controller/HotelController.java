package com.hotelbooking.controller;

import com.hotelbooking.dto.response.HotelResponse;
import com.hotelbooking.service.HotelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP adapter for hotel read APIs.
 * <p>
 * Contract: {@code docs/api/hotels.md}
 * <p>
 * This class handles HTTP only — parsing paths, delegating to {@link HotelService},
 * returning JSON. No database access or entity-to-DTO mapping here.
 */
@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    /**
     * GET /api/hotels — list all active hotels.
     */
    @GetMapping
    public List<HotelResponse> listHotels() {
        return hotelService.listActiveHotels();
    }

    /**
     * GET /api/hotels/{id} — fetch one active hotel by id.
     */
    @GetMapping("/{id}")
    public HotelResponse getHotel(@PathVariable Long id) {
        return hotelService.getActiveHotelById(id);
    }
}
