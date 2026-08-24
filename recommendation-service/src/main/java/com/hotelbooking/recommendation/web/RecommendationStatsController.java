package com.hotelbooking.recommendation.web;

import com.hotelbooking.recommendation.kafka.RecommendationBookingConsumer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal")
public class RecommendationStatsController {

    private final RecommendationBookingConsumer consumer;

    public RecommendationStatsController(RecommendationBookingConsumer consumer) {
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
