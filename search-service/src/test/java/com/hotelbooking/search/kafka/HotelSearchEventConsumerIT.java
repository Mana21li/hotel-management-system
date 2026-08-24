package com.hotelbooking.search.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Admin sync → Kafka HotelUpserted → consumer indexes the hotel.
 * Requires Docker: Postgres + Elasticsearch + Kafka.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HotelSearchEventConsumerIT {

    @DynamicPropertySource
    static void uniqueConsumerGroup(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.consumer-group", () -> "hotel-search-it-" + UUID.randomUUID());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private HotelSearchEventConsumer hotelSearchEventConsumer;

    @Test
    void syncHotel_isConsumedAndIndexed() {
        int before = hotelSearchEventConsumer.getProcessedCount();

        RestClient.create("http://localhost:" + port)
                .post()
                .uri("/api/admin/search/hotels/1/sync")
                .retrieve()
                .toBodilessEntity();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(hotelSearchEventConsumer.getProcessedCount()).isGreaterThan(before));
    }
}
