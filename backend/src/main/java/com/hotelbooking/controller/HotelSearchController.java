package com.hotelbooking.controller;

import com.hotelbooking.dto.response.HotelSearchResponse;
import com.hotelbooking.search.HotelSearchCriteria;
import com.hotelbooking.search.HotelSearchService;
import com.hotelbooking.search.HotelSearchSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public search API backed by Elasticsearch.
 * <p>
 * Read path only — booking and hotel detail still use Postgres as source of truth.
 */
@RestController
@Validated
@RequestMapping("/api/search/hotels")
public class HotelSearchController {

    private final HotelSearchService hotelSearchService;

    public HotelSearchController(HotelSearchService hotelSearchService) {
        this.hotelSearchService = hotelSearchService;
    }

    /**
     * GET /api/search/hotels — full-text search with filters, sort, and pagination.
     *
     * @param cityId   exact filter on denormalized city id
     * @param minStars minimum star rating (inclusive)
     * @param maxPrice maximum cheapest-room nightly price (inclusive)
     * @param sort     relevance (default), price_asc, price_desc, stars_desc, stars_asc
     * @param page     zero-based page index
     * @param size     page size (max 100)
     */
    @GetMapping
    public HotelSearchResponse search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) @Min(1) @Max(5) Integer minStars,
            @RequestParam(required = false) @PositiveOrZero Double maxPrice,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        HotelSearchCriteria criteria = new HotelSearchCriteria(
                query,
                cityId,
                minStars,
                maxPrice,
                HotelSearchSort.fromParam(sort),
                page,
                size
        );
        return hotelSearchService.search(criteria);
    }
}
