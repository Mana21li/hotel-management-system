package com.hotelbooking.recommendation.kafka;

/**
 * Kafka topic names shared by the recommendation consumer.
 * Retry/DLT suffixes are unique so they do not collide with notification-service.
 */
public final class KafkaTopics {

    public static final String BOOKING_EVENTS = "booking-events";
    public static final String BOOKING_EVENTS_DLT = "booking-events-recommendation-dlt";

    private KafkaTopics() {
    }
}
