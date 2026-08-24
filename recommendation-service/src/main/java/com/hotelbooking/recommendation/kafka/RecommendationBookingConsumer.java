package com.hotelbooking.recommendation.kafka;

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
 * Simulated recommendation refresh after a booking.
 * Separate consumer group → fan-out with notification and analytics.
 */
@Component
public class RecommendationBookingConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecommendationBookingConsumer.class);

    public static final String POISON_REFERENCE_PREFIX = "POISON-REC-";

    private final AtomicInteger processedCount = new AtomicInteger();
    private final AtomicInteger failureCount = new AtomicInteger();

    @RetryableTopic(
            attempts = "${app.kafka.retryable.attempts:3}",
            backoff = @Backoff(delayExpression = "${app.kafka.retryable.delay-ms:200}"),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            retryTopicSuffix = "-recommendation-retry",
            dltTopicSuffix = "-recommendation-dlt",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoCreateTopics = "true",
            kafkaTemplate = "kafkaTemplate")
    @KafkaListener(
            topics = KafkaTopics.BOOKING_EVENTS,
            groupId = "${app.kafka.consumer-group:recommendation-group}",
            properties = {
                    "spring.json.value.default.type=com.hotelbooking.recommendation.kafka.BookingCreatedEvent"
            })
    public void onBookingCreated(BookingCreatedEvent event) {
        if (event.bookingReference() != null
                && event.bookingReference().startsWith(POISON_REFERENCE_PREFIX)) {
            failureCount.incrementAndGet();
            log.warn("[Recommendation] Simulated permanent failure for poison bookingRef={}",
                    event.bookingReference());
            throw new IllegalStateException(
                    "Simulated recommendation failure for poison bookingRef=" + event.bookingReference());
        }

        log.info("[Recommendation] Would refresh recommendations for userId={} after booking hotel={}",
                event.userId(), event.hotelName());
        processedCount.incrementAndGet();
    }

    public int getProcessedCount() {
        return processedCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }
}
