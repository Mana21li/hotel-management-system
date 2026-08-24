package com.hotelbooking.controller;

import com.hotelbooking.dto.response.ReindexResponse;
import com.hotelbooking.search.SearchServiceGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin search endpoints — forwarded to search-service (same public paths).
 */
@RestController
@RequestMapping("/api/admin/search/hotels")
public class HotelSearchAdminController {

    private final SearchServiceGateway searchServiceGateway;

    public HotelSearchAdminController(SearchServiceGateway searchServiceGateway) {
        this.searchServiceGateway = searchServiceGateway;
    }

    @PostMapping("/reindex")
    public ResponseEntity<ReindexResponse> reindex() {
        return ResponseEntity.ok(searchServiceGateway.reindex());
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<Map<String, Object>> syncHotel(@PathVariable Long id) {
        return ResponseEntity.accepted().body(searchServiceGateway.syncHotel(id));
    }
}
