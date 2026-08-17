package com.hotelbooking.kafka.consumer;

import com.hotelbooking.config.KafkaConfig;
import com.hotelbooking.kafka.event.BookingCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates analytics / metrics recording for a new booking.
 * <p>
 * Separate consumer group → fan-out: every BookingCreated is seen here too.
 */
@Component
public class AnalyticsBookingConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsBookingConsumer.class);

    private final AtomicInteger processedCount = new AtomicInteger();

    @KafkaListener(
            topics = KafkaConfig.BOOKING_EVENTS_TOPIC,
            groupId = "${app.kafka.consumer-groups.analytics:analytics-group}",
            properties = {
                    "spring.json.value.default.type=com.hotelbooking.kafka.event.BookingCreatedEvent"
            })
    public void onBookingCreated(BookingCreatedEvent event) {
        log.info("[Analytics] Would record metric booking_created bookingId={} totalAmount={} nights={}",
                event.bookingId(), event.totalAmount(), event.nights());
        processedCount.incrementAndGet();
    }

    public int getProcessedCount() {
        return processedCount.get();
    }
}
