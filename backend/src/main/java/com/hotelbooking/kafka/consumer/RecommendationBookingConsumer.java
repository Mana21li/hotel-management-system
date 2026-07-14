package com.hotelbooking.kafka.consumer;

import com.hotelbooking.config.KafkaConfig;
import com.hotelbooking.kafka.event.BookingCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates refreshing hotel/user recommendations after a booking.
 * <p>
 * Separate consumer group → fan-out with notification and analytics.
 */
@Component
public class RecommendationBookingConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecommendationBookingConsumer.class);

    private final AtomicInteger processedCount = new AtomicInteger();

    @KafkaListener(
            topics = KafkaConfig.BOOKING_EVENTS_TOPIC,
            groupId = "${app.kafka.consumer-groups.recommendation:recommendation-group}",
            properties = {
                    "spring.json.value.default.type=com.hotelbooking.kafka.event.BookingCreatedEvent"
            })
    public void onBookingCreated(BookingCreatedEvent event) {
        log.info("[Recommendation] Would refresh recommendations for userId={} after booking hotel={}",
                event.userId(), event.hotelName());
        processedCount.incrementAndGet();
    }

    public int getProcessedCount() {
        return processedCount.get();
    }
}
