package com.hotelbooking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic declarations. Retry/DLT topics for {@code @RetryableTopic} are auto-created.
 */
@Configuration
public class KafkaConfig {

    public static final String BOOKING_EVENTS_TOPIC = "booking-events";

    /** Non-blocking retry DLT topic name produced by {@code @RetryableTopic} (suffix {@code -dlt}). */
    public static final String BOOKING_EVENTS_DLT_TOPIC = "booking-events-dlt";

    public static final String HOTEL_EVENTS_TOPIC = "hotel-events";

    public static final String HOTEL_EVENTS_DLT_TOPIC = "hotel-events-dlt";

    @Bean
    NewTopic bookingEventsTopic(
            @Value("${app.kafka.topics.booking-events.partitions:3}") int partitions) {
        return TopicBuilder.name(BOOKING_EVENTS_TOPIC)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic hotelEventsTopic(
            @Value("${app.kafka.topics.hotel-events.partitions:3}") int partitions) {
        return TopicBuilder.name(HOTEL_EVENTS_TOPIC)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
