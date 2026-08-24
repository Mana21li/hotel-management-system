package com.hotelbooking.search.controller;

import com.hotelbooking.search.dto.ReindexResponse;
import com.hotelbooking.search.HotelSearchSyncService;
import com.hotelbooking.search.kafka.HotelSearchEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for the Elasticsearch search read model.
 */
@RestController
@RequestMapping("/api/admin/search/hotels")
public class HotelSearchAdminController {

    private final HotelSearchSyncService hotelSearchSyncService;
    private final HotelSearchEventService hotelSearchEventService;

    public HotelSearchAdminController(
            HotelSearchSyncService hotelSearchSyncService,
            HotelSearchEventService hotelSearchEventService) {
        this.hotelSearchSyncService = hotelSearchSyncService;
        this.hotelSearchEventService = hotelSearchEventService;
    }

    /**
     * POST /api/admin/search/hotels/reindex — full rebuild (direct bulk to ES).
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

    /**
     * POST /api/admin/search/hotels/{id}/sync — enqueue HotelUpserted via outbox
     * (Kafka → ES consumer). Prefer this for incremental updates.
     */
    @PostMapping("/{id}/sync")
    public ResponseEntity<Map<String, Object>> syncHotel(@PathVariable Long id) {
        hotelSearchEventService.publishHotelUpsert(id);
        return ResponseEntity.accepted().body(Map.of(
                "hotelId", id,
                "message", "HotelUpserted enqueued to outbox; relay will publish to hotel-events"
        ));
    }
}
