package com.hotelbooking.analytics.kafka;

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
 * Simulated analytics / metrics recording for a new booking.
 * Separate consumer group → fan-out with notification and recommendation.
 */
@Component
public class AnalyticsBookingConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsBookingConsumer.class);

    public static final String POISON_REFERENCE_PREFIX = "POISON-AN-";

    private final AtomicInteger processedCount = new AtomicInteger();
    private final AtomicInteger failureCount = new AtomicInteger();

    @RetryableTopic(
            attempts = "${app.kafka.retryable.attempts:3}",
            backoff = @Backoff(delayExpression = "${app.kafka.retryable.delay-ms:200}"),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            retryTopicSuffix = "-analytics-retry",
            dltTopicSuffix = "-analytics-dlt",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoCreateTopics = "true",
            kafkaTemplate = "kafkaTemplate")
    @KafkaListener(
            topics = KafkaTopics.BOOKING_EVENTS,
            groupId = "${app.kafka.consumer-group:analytics-group}",
            properties = {
                    "spring.json.value.default.type=com.hotelbooking.analytics.kafka.BookingCreatedEvent"
            })
    public void onBookingCreated(BookingCreatedEvent event) {
        if (event.bookingReference() != null
                && event.bookingReference().startsWith(POISON_REFERENCE_PREFIX)) {
            failureCount.incrementAndGet();
            log.warn("[Analytics] Simulated permanent failure for poison bookingRef={}",
                    event.bookingReference());
            throw new IllegalStateException(
                    "Simulated analytics failure for poison bookingRef=" + event.bookingReference());
        }

        log.info("[Analytics] Would record metric booking_created bookingId={} totalAmount={} nights={}",
                event.bookingId(), event.totalAmount(), event.nights());
        processedCount.incrementAndGet();
    }

    public int getProcessedCount() {
        return processedCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }
}
