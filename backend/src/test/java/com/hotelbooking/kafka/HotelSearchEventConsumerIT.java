package com.hotelbooking.kafka;

import com.hotelbooking.kafka.consumer.HotelSearchEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies Kafka-driven ES sync: admin sync enqueues HotelUpserted via outbox,
 * relay publishes to hotel-events, consumer indexes the hotel.
 * <p>
 * Requires Docker: Postgres + Redis + Elasticsearch + Kafka.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HotelSearchEventConsumerIT {

    @DynamicPropertySource
    static void uniqueConsumerGroups(DynamicPropertyRegistry registry) {
        KafkaTestConsumerGroups.registerUniqueGroups(registry);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private HotelSearchEventConsumer hotelSearchEventConsumer;

    @Test
    void syncHotel_isConsumedAndIndexed() {
        int before = hotelSearchEventConsumer.getProcessedCount();

        RestClient client = RestClient.create("http://localhost:" + port);
        client.post()
                .uri("/api/admin/search/hotels/1/sync")
                .retrieve()
                .toBodilessEntity();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(hotelSearchEventConsumer.getProcessedCount()).isGreaterThan(before));
    }
}
