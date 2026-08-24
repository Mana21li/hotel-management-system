package com.hotelbooking.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Fan-out: one {@code BookingCreated} is processed by three extracted consumers
 * because they use <em>different</em> Kafka groups.
 * <p>
 * Requires notification (:8082), analytics (:8084), recommendation (:8085).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingFanoutE2EIT {

    private static final String NOTIFICATION_URL =
            System.getenv().getOrDefault("NOTIFICATION_SERVICE_URL", "http://127.0.0.1:8082");
    private static final String ANALYTICS_URL =
            System.getenv().getOrDefault("ANALYTICS_SERVICE_URL", "http://127.0.0.1:8084");
    private static final String RECOMMENDATION_URL =
            System.getenv().getOrDefault("RECOMMENDATION_SERVICE_URL", "http://127.0.0.1:8085");

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createBooking_isProcessedByAllExtractedConsumers() throws Exception {
        RestClient bookingClient = RestClient.create("http://localhost:" + port);
        RestClient notification = RestClient.create(NOTIFICATION_URL);
        RestClient analytics = RestClient.create(ANALYTICS_URL);
        RestClient recommendation = RestClient.create(RECOMMENDATION_URL);

        int notificationBefore = readProcessedCount(notification);
        int analyticsBefore = readProcessedCount(analytics);
        int recommendationBefore = readProcessedCount(recommendation);

        long dayOffset = Math.abs(UUID.randomUUID().getMostSignificantBits() % 10_000);
        LocalDate checkIn = LocalDate.of(2042, 1, 1).plusDays(dayOffset);
        LocalDate checkOut = checkIn.plusDays(2);

        String body = """
                {"userId":1,"roomId":20,"checkInDate":"%s","checkOutDate":"%s"}
                """.formatted(checkIn, checkOut);

        ResponseEntity<Void> bookingResponse = bookingClient.post()
                .uri("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        assertThat(bookingResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            assertThat(readProcessedCount(notification)).isGreaterThan(notificationBefore);
            assertThat(readProcessedCount(analytics)).isGreaterThan(analyticsBefore);
            assertThat(readProcessedCount(recommendation)).isGreaterThan(recommendationBefore);
        });
    }

    private int readProcessedCount(RestClient client) throws Exception {
        String stats = client.get()
                .uri("/internal/stats")
                .retrieve()
                .body(String.class);
        JsonNode json = objectMapper.readTree(stats);
        return json.get("processedCount").asInt();
    }
}
