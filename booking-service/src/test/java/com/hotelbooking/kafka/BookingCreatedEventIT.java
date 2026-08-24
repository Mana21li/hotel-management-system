package com.hotelbooking.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.config.KafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a successful booking eventually publishes {@code BookingCreated} to Kafka
 * via the transactional outbox relay (not a direct dual-write).
 * <p>
 * Requires Docker: Postgres + Redis + Elasticsearch + Kafka.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingCreatedEventIT {

    @DynamicPropertySource
    static void uniqueConsumerGroups(DynamicPropertyRegistry registry) {
        KafkaTestConsumerGroups.registerUniqueGroups(registry);
    }

    @LocalServerPort
    private int port;

    @Test
    void createBooking_publishesBookingCreatedEvent() throws Exception {
        long dayOffset = Math.abs(UUID.randomUUID().getMostSignificantBits() % 200_000);
        LocalDate checkIn = LocalDate.of(2051, 6, 1).plusDays(dayOffset);
        LocalDate checkOut = checkIn.plusDays(2);

        String groupId = "booking-created-it-" + UUID.randomUUID();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
        ))) {
            consumer.subscribe(List.of(KafkaConfig.BOOKING_EVENTS_TOPIC));
            // Join group / get assignment before producing
            consumer.poll(Duration.ofMillis(500));

            RestClient client = RestClient.create("http://localhost:" + port);
            String body = """
                    {"userId":1,"roomId":20,"checkInDate":"%s","checkOutDate":"%s"}
                    """.formatted(checkIn, checkOut);

            String response = client.post()
                    .uri("/api/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            JsonNode bookingJson = mapper.readTree(response);
            String bookingReference = bookingJson.get("bookingReference").asText();
            long bookingId = bookingJson.get("bookingId").asLong();

            ConsumerRecord<String, String> matched = null;
            // Outbox relay polls every ~500ms; allow time for enqueue → publish.
            long deadline = System.currentTimeMillis() + 25_000;
            while (matched == null && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().contains(bookingReference)) {
                        matched = record;
                        break;
                    }
                }
            }

            assertThat(matched)
                    .as("expected BookingCreated event for %s on topic %s",
                            bookingReference, KafkaConfig.BOOKING_EVENTS_TOPIC)
                    .isNotNull();
            assertThat(matched.key()).isEqualTo("20"); // roomId key (Milestone 6 ordering)
            assertThat(matched.value()).contains("\"eventType\":\"BookingCreated\"");
            assertThat(matched.value()).contains("\"roomId\":20");
        }
    }
}
