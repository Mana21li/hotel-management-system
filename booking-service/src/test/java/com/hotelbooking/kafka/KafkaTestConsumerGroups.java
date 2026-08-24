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
        // Analytics and Recommendation now live in extracted services.
        // Keep this hook so existing @SpringBootTest classes compile and stay unique-ready.
        String suffix = UUID.randomUUID().toString();
        registry.add("app.kafka.test-group-suffix", () -> suffix);
    }
}
