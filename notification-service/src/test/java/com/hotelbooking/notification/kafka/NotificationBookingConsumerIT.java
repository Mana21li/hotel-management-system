package com.hotelbooking.notification.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the extracted notification consumer processes {@code BookingCreated} from Kafka.
 * Requires Docker Kafka on localhost:9092.
 */
@SpringBootTest
class NotificationBookingConsumerIT {

    @DynamicPropertySource
    static void uniqueConsumerGroup(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.consumer-group", () -> "notification-it-" + UUID.randomUUID());
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private NotificationBookingConsumer notificationBookingConsumer;

    @Test
    void consumesBookingCreatedEvent() throws Exception {
        int before = notificationBookingConsumer.getProcessedCount();
        String bookingRef = "BK-NOTIF-IT-" + UUID.randomUUID();

        BookingCreatedEvent event = new BookingCreatedEvent(
                BookingCreatedEvent.TYPE,
                88_001L,
                bookingRef,
                1L,
                20L,
                "Notification IT Hotel",
                LocalDate.of(2034, 1, 1),
                LocalDate.of(2034, 1, 3),
                2,
                new BigDecimal("5000.00"),
                "PENDING",
                Instant.now()
        );

        kafkaTemplate.send(KafkaTopics.BOOKING_EVENTS, String.valueOf(event.roomId()), event).get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notificationBookingConsumer.getProcessedCount()).isGreaterThan(before));
    }
}
