package com.hotelbooking.service;

import com.hotelbooking.config.KafkaConfig;
import com.hotelbooking.exception.HotelNotFoundException;
import com.hotelbooking.kafka.event.HotelUpsertedEvent;
import com.hotelbooking.outbox.OutboxService;
import com.hotelbooking.repository.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enqueues hotel search sync events (transactional outbox → Kafka → ES consumer).
 */
@Service
public class HotelSearchEventService {

    private final HotelRepository hotelRepository;
    private final OutboxService outboxService;

    public HotelSearchEventService(HotelRepository hotelRepository, OutboxService outboxService) {
        this.hotelRepository = hotelRepository;
        this.outboxService = outboxService;
    }

    /**
     * Queues a {@link HotelUpsertedEvent} so Elasticsearch is updated asynchronously.
     */
    @Transactional
    public void enqueueHotelUpsert(Long hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        outboxService.enqueue(
                KafkaConfig.HOTEL_EVENTS_TOPIC,
                String.valueOf(hotelId),
                HotelUpsertedEvent.TYPE,
                HotelUpsertedEvent.of(hotelId));
    }
}
