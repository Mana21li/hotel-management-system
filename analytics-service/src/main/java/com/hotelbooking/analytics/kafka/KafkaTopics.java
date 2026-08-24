package com.hotelbooking.analytics.kafka;

/**
 * Kafka topic names shared by the analytics consumer.
 * Retry/DLT suffixes are unique so they do not collide with notification-service.
 */
public final class KafkaTopics {

    public static final String BOOKING_EVENTS = "booking-events";
    public static final String BOOKING_EVENTS_DLT = "booking-events-analytics-dlt";

    private KafkaTopics() {
    }
}
