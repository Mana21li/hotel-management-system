package com.hotelbooking.search.web;

import com.hotelbooking.search.kafka.HotelSearchEventConsumer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal")
public class SearchStatsController {

    private final HotelSearchEventConsumer consumer;

    public SearchStatsController(HotelSearchEventConsumer consumer) {
        this.consumer = consumer;
    }

    @GetMapping("/stats")
    public Map<String, Integer> stats() {
        return Map.of("processedCount", consumer.getProcessedCount());
    }
}
