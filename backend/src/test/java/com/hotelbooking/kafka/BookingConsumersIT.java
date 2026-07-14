package com.hotelbooking.kafka;

import com.hotelbooking.kafka.consumer.AnalyticsBookingConsumer;
import com.hotelbooking.kafka.consumer.NotificationBookingConsumer;
import com.hotelbooking.kafka.consumer.RecommendationBookingConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

/**
 * Verifies fan-out: one BookingCreated event is processed by all three consumer groups.
 * <p>
 * Requires Docker: Postgres + Redis + Elasticsearch + Kafka.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingConsumersIT {

    @DynamicPropertySource
    static void uniqueConsumerGroups(DynamicPropertyRegistry registry) {
        KafkaTestConsumerGroups.registerUniqueGroups(registry);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private NotificationBookingConsumer notificationBookingConsumer;

    @Autowired
    private AnalyticsBookingConsumer analyticsBookingConsumer;

    @Autowired
    private RecommendationBookingConsumer recommendationBookingConsumer;

    @Test
    void createBooking_isHandledByAllThreeConsumerGroups() {
        int notificationBefore = notificationBookingConsumer.getProcessedCount();
        int analyticsBefore = analyticsBookingConsumer.getProcessedCount();
        int recommendationBefore = recommendationBookingConsumer.getProcessedCount();

        long dayOffset = System.nanoTime() % 5000;
        LocalDate checkIn = LocalDate.of(2029, 3, 1).plusDays(dayOffset);
        LocalDate checkOut = checkIn.plusDays(2);

        RestClient client = RestClient.create("http://localhost:" + port);
        String body = """
                {"userId":2,"roomId":20,"checkInDate":"%s","checkOutDate":"%s"}
                """.formatted(checkIn, checkOut);

        client.post()
                .uri("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        // Outbox relay + three consumer groups.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(notificationBookingConsumer.getProcessedCount())
                    .isGreaterThan(notificationBefore);
            assertThat(analyticsBookingConsumer.getProcessedCount())
                    .isGreaterThan(analyticsBefore);
            assertThat(recommendationBookingConsumer.getProcessedCount())
                    .isGreaterThan(recommendationBefore);
        });
    }
}
