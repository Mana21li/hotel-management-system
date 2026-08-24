package com.hotelbooking.notification.kafka;

/**
 * Kafka topic names shared by the notification consumer.
 */
public final class KafkaTopics {

    public static final String BOOKING_EVENTS = "booking-events";
    public static final String BOOKING_EVENTS_DLT = "booking-events-dlt";

    private KafkaTopics() {
    }
}
