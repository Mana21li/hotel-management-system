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
 * End-to-end: public {@code POST /api/bookings} on the strangler (:8080)
 * → booking-service outbox → Kafka → extracted notification-service.
 * <p>
 * Requires Docker infra, booking-service, and notification-service on
 * {@code NOTIFICATION_SERVICE_URL}. Stop any stale {@code hms_app} that
 * still joins {@code notification-group} — it will steal messages.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingNotificationE2EIT {

    private static final String NOTIFICATION_URL =
            System.getenv().getOrDefault("NOTIFICATION_SERVICE_URL", "http://127.0.0.1:8082");

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createBooking_isProcessedByExtractedNotificationService() throws Exception {
        RestClient notificationClient = RestClient.create(NOTIFICATION_URL);
        RestClient bookingClient = RestClient.create("http://localhost:" + port);

        int processedBefore = readProcessedCount(notificationClient);

        long dayOffset = Math.abs(UUID.randomUUID().getMostSignificantBits() % 10_000);
        LocalDate checkIn = LocalDate.of(2041, 1, 1).plusDays(dayOffset);
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

        await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                assertThat(readProcessedCount(notificationClient)).isGreaterThan(processedBefore));
    }

    private int readProcessedCount(RestClient notificationClient) throws Exception {
        String stats = notificationClient.get()
                .uri("/internal/stats")
                .retrieve()
                .body(String.class);
        JsonNode json = objectMapper.readTree(stats);
        return json.get("processedCount").asInt();
    }
}
