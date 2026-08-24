package com.hotelbooking.controller;

import com.hotelbooking.dto.response.HotelSearchResponse;
import com.hotelbooking.search.SearchServiceGateway;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public search API — same paths as before. Implementation lives in search-service.
 */
@RestController
@Validated
@RequestMapping("/api/search/hotels")
public class HotelSearchController {

    private final SearchServiceGateway searchServiceGateway;

    public HotelSearchController(SearchServiceGateway searchServiceGateway) {
        this.searchServiceGateway = searchServiceGateway;
    }

    @GetMapping
    public HotelSearchResponse search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) @Min(1) @Max(5) Integer minStars,
            @RequestParam(required = false) @PositiveOrZero Double maxPrice,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return searchServiceGateway.search(query, cityId, minStars, maxPrice, sort, page, size);
    }
}
