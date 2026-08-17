package com.hotelbooking.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.entity.OutboxEvent;
import com.hotelbooking.kafka.event.BookingCreatedEvent;
import com.hotelbooking.kafka.event.HotelUpsertedEvent;
import com.hotelbooking.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls unpublished outbox rows and publishes them to Kafka, then marks them published.
 * <p>
 * Closes the dual-write gap: if the DB transaction committed, the event is durable
 * in Postgres even if Kafka was down at write time.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public OutboxRelay(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.outbox.batch-size:50}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextUnpublished(batchSize);
        for (OutboxEvent row : batch) {
            try {
                Object payload = deserialize(row);
                kafkaTemplate
                        .send(row.getTopic(), row.getMessageKey(), payload)
                        .get(5, TimeUnit.SECONDS);
                row.setPublishedAt(Instant.now());
                outboxEventRepository.save(row);
                log.debug("Outbox published id={} type={} topic={}",
                        row.getId(), row.getEventType(), row.getTopic());
            } catch (Exception ex) {
                // At-least-once: leave unpublished and try again on the next poll.
                log.error("Outbox publish failed id={} type={} — will retry",
                        row.getId(), row.getEventType(), ex);
            }
        }
    }

    private Object deserialize(OutboxEvent row) throws Exception {
        return switch (row.getEventType()) {
            case BookingCreatedEvent.TYPE ->
                    objectMapper.readValue(row.getPayload(), BookingCreatedEvent.class);
            case HotelUpsertedEvent.TYPE ->
                    objectMapper.readValue(row.getPayload(), HotelUpsertedEvent.class);
            default -> objectMapper.readTree(row.getPayload());
        };
    }
}
