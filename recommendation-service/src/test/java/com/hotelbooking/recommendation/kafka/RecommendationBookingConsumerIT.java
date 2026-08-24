package com.hotelbooking.recommendation.kafka;

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

@SpringBootTest
class RecommendationBookingConsumerIT {

    @DynamicPropertySource
    static void uniqueConsumerGroup(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.consumer-group", () -> "recommendation-it-" + UUID.randomUUID());
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RecommendationBookingConsumer recommendationBookingConsumer;

    @Test
    void consumesBookingCreatedEvent() throws Exception {
        int before = recommendationBookingConsumer.getProcessedCount();
        String bookingRef = "BK-REC-IT-" + UUID.randomUUID();

        BookingCreatedEvent event = new BookingCreatedEvent(
                BookingCreatedEvent.TYPE,
                88_201L,
                bookingRef,
                2L,
                20L,
                "Recommendation IT Hotel",
                LocalDate.of(2034, 2, 1),
                LocalDate.of(2034, 2, 3),
                2,
                new BigDecimal("6100.00"),
                "PENDING",
                Instant.now()
        );

        kafkaTemplate.send(KafkaTopics.BOOKING_EVENTS, String.valueOf(event.roomId()), event).get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(recommendationBookingConsumer.getProcessedCount()).isGreaterThan(before));
    }
}
