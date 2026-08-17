package com.hotelbooking.kafka;

import com.hotelbooking.config.KafkaConfig;
import com.hotelbooking.kafka.consumer.NotificationBookingConsumer;
import com.hotelbooking.kafka.event.BookingCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies retry + Dead Letter Topic: a poison BookingCreated is retried, then lands on DLT.
 * <p>
 * Requires Docker Kafka (and full Spring stack deps: Postgres/Redis/ES).
 */
@SpringBootTest
class BookingEventDltIT {

    @DynamicPropertySource
    static void uniqueConsumerGroups(DynamicPropertyRegistry registry) {
        KafkaTestConsumerGroups.registerUniqueGroups(registry);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void poisonMessage_isRetriedThenPublishedToDlt() throws Exception {
        String poisonRef = NotificationBookingConsumer.POISON_REFERENCE_PREFIX + UUID.randomUUID();
        BookingCreatedEvent poison = new BookingCreatedEvent(
                BookingCreatedEvent.TYPE,
                9_999_001L,
                poisonRef,
                1L,
                20L,
                "Poison Hotel",
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 1, 3),
                2,
                new BigDecimal("1000.00"),
                "PENDING",
                Instant.now()
        );

        String groupId = "dlt-it-" + UUID.randomUUID();
        try (KafkaConsumer<String, String> dltConsumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()
        ))) {
            dltConsumer.subscribe(List.of(KafkaConfig.BOOKING_EVENTS_DLT_TOPIC));
            // Join group before producing so we do not miss the DLT publish.
            dltConsumer.poll(Duration.ofMillis(1000));

            kafkaTemplate.send(KafkaConfig.BOOKING_EVENTS_TOPIC, String.valueOf(poison.roomId()), poison)
                    .get();

            ConsumerRecord<String, String> dltRecord = null;
            long deadline = System.currentTimeMillis() + 45_000;
            while (dltRecord == null && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().contains(poisonRef)) {
                        dltRecord = record;
                        break;
                    }
                }
            }

            assertThat(dltRecord)
                    .as("poison event should appear on %s after retries", KafkaConfig.BOOKING_EVENTS_DLT_TOPIC)
                    .isNotNull();
            assertThat(dltRecord.key()).isEqualTo(String.valueOf(poison.roomId()));
            assertThat(dltRecord.value()).contains(poisonRef);
        }
    }
}
