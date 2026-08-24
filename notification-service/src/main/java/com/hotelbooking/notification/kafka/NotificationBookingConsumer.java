package com.hotelbooking.notification.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates sending a booking confirmation (email/SMS).
 * Extracted consumer for {@code booking-events} — independent of the booking write path.
 */
@Component
public class NotificationBookingConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationBookingConsumer.class);

    public static final String POISON_REFERENCE_PREFIX = "POISON-";

    private final AtomicInteger processedCount = new AtomicInteger();
    private final AtomicInteger failureCount = new AtomicInteger();

    @RetryableTopic(
            attempts = "${app.kafka.retryable.attempts:3}",
            backoff = @Backoff(delayExpression = "${app.kafka.retryable.delay-ms:200}"),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoCreateTopics = "true",
            kafkaTemplate = "kafkaTemplate")
    @KafkaListener(
            topics = KafkaTopics.BOOKING_EVENTS,
            groupId = "${app.kafka.consumer-group:notification-group}",
            properties = {
                    "spring.json.value.default.type=com.hotelbooking.notification.kafka.BookingCreatedEvent"
            })
    public void onBookingCreated(BookingCreatedEvent event) {
        if (event.bookingReference() != null
                && event.bookingReference().startsWith(POISON_REFERENCE_PREFIX)) {
            failureCount.incrementAndGet();
            log.warn("[Notification] Simulated permanent failure for poison bookingRef={}",
                    event.bookingReference());
            throw new IllegalStateException(
                    "Simulated notification failure for poison bookingRef=" + event.bookingReference());
        }

        log.info("[Notification] Would send confirmation email for bookingRef={} userId={} hotel={}",
                event.bookingReference(), event.userId(), event.hotelName());
        processedCount.incrementAndGet();
    }

    public int getProcessedCount() {
        return processedCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }
}
