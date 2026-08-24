package com.hotelbooking.analytics.web;

import com.hotelbooking.analytics.kafka.AnalyticsBookingConsumer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal")
public class AnalyticsStatsController {

    private final AnalyticsBookingConsumer consumer;

    public AnalyticsStatsController(AnalyticsBookingConsumer consumer) {
        this.consumer = consumer;
    }

    @GetMapping("/stats")
    public Map<String, Integer> stats() {
        return Map.of(
                "processedCount", consumer.getProcessedCount(),
                "failureCount", consumer.getFailureCount()
        );
    }
}
