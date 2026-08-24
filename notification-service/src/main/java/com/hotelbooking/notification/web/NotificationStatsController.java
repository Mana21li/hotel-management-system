package com.hotelbooking.notification.web;

import com.hotelbooking.notification.kafka.NotificationBookingConsumer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test/ops visibility for processed messages (used by integration tests and manual verification).
 */
@RestController
@RequestMapping("/internal")
public class NotificationStatsController {

    private final NotificationBookingConsumer consumer;

    public NotificationStatsController(NotificationBookingConsumer consumer) {
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
