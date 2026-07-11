package com.hotelbooking.controller;

import com.hotelbooking.dto.response.ReindexResponse;
import com.hotelbooking.search.HotelSearchSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for the Elasticsearch search read model.
 * <p>
 * No auth in this learning project — in production these would be protected
 * (admin role, internal network, or API key).
 */
@RestController
@RequestMapping("/api/admin/search/hotels")
public class HotelSearchAdminController {

    private final HotelSearchSyncService hotelSearchSyncService;

    public HotelSearchAdminController(HotelSearchSyncService hotelSearchSyncService) {
        this.hotelSearchSyncService = hotelSearchSyncService;
    }

    /**
     * POST /api/admin/search/hotels/reindex — full rebuild of the hotels search index
     * from PostgreSQL (source of truth).
     */
    @PostMapping("/reindex")
    public ResponseEntity<ReindexResponse> reindex() {
        HotelSearchSyncService.ReindexResult result = hotelSearchSyncService.reindexAll();

        String message = result.failures() == 0
                ? "Reindex completed successfully"
                : "Reindex completed with " + result.failures() + " failure(s)";

        return ResponseEntity.ok(new ReindexResponse(
                result.readFromPostgres(),
                result.indexed(),
                result.failures(),
                message
        ));
    }
}
