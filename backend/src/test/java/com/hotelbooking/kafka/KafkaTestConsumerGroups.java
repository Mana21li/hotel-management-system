package com.hotelbooking.kafka;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.UUID;

/**
 * Gives each Spring test context unique Kafka consumer group ids so two
 * {@code @SpringBootTest} classes in the same JVM do not share partitions
 * and steal each other's messages.
 */
public final class KafkaTestConsumerGroups {

    private KafkaTestConsumerGroups() {
    }

    public static void registerUniqueGroups(DynamicPropertyRegistry registry) {
        String suffix = UUID.randomUUID().toString();
        registry.add("app.kafka.consumer-groups.notification", () -> "notification-group-" + suffix);
        registry.add("app.kafka.consumer-groups.analytics", () -> "analytics-group-" + suffix);
        registry.add("app.kafka.consumer-groups.recommendation", () -> "recommendation-group-" + suffix);
        registry.add("app.kafka.consumer-groups.hotel-search", () -> "hotel-search-group-" + suffix);
    }
}
