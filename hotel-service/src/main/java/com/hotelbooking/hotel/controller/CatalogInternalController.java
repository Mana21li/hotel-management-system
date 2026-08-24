package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.dto.HotelSearchProjectionResponse;
import com.hotelbooking.hotel.dto.HotelSummaryResponse;
import com.hotelbooking.hotel.dto.RoomCatalogResponse;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.service.CatalogProjectionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service catalog API. Not exposed via the :8080 strangler.
 * booking-service and search-service call these endpoints instead of sharing Postgres.
 */
@RestController
@RequestMapping("/internal/catalog")
public class CatalogInternalController {

    private final CatalogProjectionService catalogProjectionService;

    public CatalogInternalController(CatalogProjectionService catalogProjectionService) {
        this.catalogProjectionService = catalogProjectionService;
    }

    @GetMapping("/rooms/{roomId}")
    public RoomCatalogResponse getRoom(@PathVariable Long roomId) {
        return catalogProjectionService.getRoom(roomId);
    }

    @GetMapping("/hotels/{hotelId}")
    public HotelSummaryResponse getHotel(@PathVariable Long hotelId) {
        return catalogProjectionService.getHotelSummary(hotelId);
    }

    @GetMapping("/hotels/{hotelId}/exists")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hotelExists(@PathVariable Long hotelId) {
        if (!catalogProjectionService.hotelExists(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
    }

    @GetMapping("/hotels/{hotelId}/search-projection")
    public HotelSearchProjectionResponse getSearchProjection(@PathVariable Long hotelId) {
        return catalogProjectionService.getSearchProjection(hotelId);
    }

    @GetMapping("/hotels/search-projections")
    public List<HotelSearchProjectionResponse> listSearchProjections() {
        return catalogProjectionService.listSearchProjections();
    }
}
