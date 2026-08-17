package com.hotelbooking.kafka.consumer;

import com.hotelbooking.config.KafkaConfig;
import com.hotelbooking.kafka.event.HotelUpsertedEvent;
import com.hotelbooking.search.HotelSearchSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka-driven Elasticsearch sync: on {@link HotelUpsertedEvent}, reload the hotel
 * document from Postgres and upsert into the {@code hotels} index.
 */
@Component
public class HotelSearchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchEventConsumer.class);

    private final HotelSearchSyncService hotelSearchSyncService;
    private final AtomicInteger processedCount = new AtomicInteger();

    public HotelSearchEventConsumer(HotelSearchSyncService hotelSearchSyncService) {
        this.hotelSearchSyncService = hotelSearchSyncService;
    }

    @RetryableTopic(
            attempts = "${app.kafka.retryable.attempts:3}",
            backoff = @Backoff(delayExpression = "${app.kafka.retryable.delay-ms:200}"),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoCreateTopics = "true",
            kafkaTemplate = "kafkaTemplate")
    @KafkaListener(
            topics = KafkaConfig.HOTEL_EVENTS_TOPIC,
            groupId = "${app.kafka.consumer-groups.hotel-search:hotel-search-group}",
            properties = {
                    "spring.json.value.default.type=com.hotelbooking.kafka.event.HotelUpsertedEvent"
            })
    public void onHotelUpserted(HotelUpsertedEvent event) {
        log.info("[HotelSearch] Upserting hotelId={} into Elasticsearch", event.hotelId());
        hotelSearchSyncService.indexHotelById(event.hotelId());
        processedCount.incrementAndGet();
    }

    public int getProcessedCount() {
        return processedCount.get();
    }
}
