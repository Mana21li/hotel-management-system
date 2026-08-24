package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.HotelResponse;
import com.hotelbooking.hotel.service.HotelCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelCatalogService hotelCatalogService;

    public HotelController(HotelCatalogService hotelCatalogService) {
        this.hotelCatalogService = hotelCatalogService;
    }

    @GetMapping
    public List<HotelResponse> listHotels() {
        return hotelCatalogService.listActiveHotels();
    }

    @GetMapping("/{id}")
    public HotelResponse getHotel(@PathVariable Long id) {
        return hotelCatalogService.getActiveHotelById(id);
    }
}
