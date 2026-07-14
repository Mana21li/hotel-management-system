package com.hotelbooking.kafka;

import com.hotelbooking.config.KafkaConfig;
import com.hotelbooking.kafka.event.BookingCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates partition key routing: same key → same partition.
 * <p>
 * Requires Docker Kafka (+ Postgres/Redis/ES for Spring context).
 */
@SpringBootTest
class BookingEventPartitionKeyIT {

    @DynamicPropertySource
    static void uniqueConsumerGroups(DynamicPropertyRegistry registry) {
        KafkaTestConsumerGroups.registerUniqueGroups(registry);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void sameRoomIdKey_routesToSamePartition() throws Exception {
        long roomId = 20L;
        String key = String.valueOf(roomId);

        SendResult<String, Object> first = send(key, event(9_001L, roomId));
        SendResult<String, Object> second = send(key, event(9_002L, roomId));

        assertThat(first.getRecordMetadata().partition())
                .as("same roomId key must map to the same partition")
                .isEqualTo(second.getRecordMetadata().partition());
    }

    @Test
    void differentRoomIdKeys_canLandOnDifferentPartitions() throws Exception {
        SendResult<String, Object> room20 = send("20", event(9_011L, 20L));
        SendResult<String, Object> room7 = send("7", event(9_012L, 7L));

        // Not guaranteed for every pair of keys, but with 3 partitions these two differ
        // for the default murmur2 hash — documents that key choice affects placement.
        assertThat(room20.getRecordMetadata().partition())
                .isNotEqualTo(room7.getRecordMetadata().partition());
    }

    private SendResult<String, Object> send(String key, BookingCreatedEvent event) throws Exception {
        return kafkaTemplate
                .send(KafkaConfig.BOOKING_EVENTS_TOPIC, key, event)
                .get(10, TimeUnit.SECONDS);
    }

    private static BookingCreatedEvent event(long bookingId, long roomId) {
        return new BookingCreatedEvent(
                BookingCreatedEvent.TYPE,
                bookingId,
                "BK-PARTITION-TEST-" + bookingId,
                1L,
                roomId,
                "Partition Test Hotel",
                LocalDate.of(2031, 1, 1),
                LocalDate.of(2031, 1, 2),
                1,
                new BigDecimal("100.00"),
                "PENDING",
                Instant.now()
        );
    }
}
