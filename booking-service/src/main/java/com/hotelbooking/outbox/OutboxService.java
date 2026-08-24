package com.hotelbooking.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.entity.OutboxEvent;
import com.hotelbooking.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

/**
 * Enqueues domain events into the outbox table (same transaction as the caller).
 */
@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String topic, String messageKey, String eventType, Object payload) {
        try {
            OutboxEvent event = OutboxEvent.builder()
                    .topic(topic)
                    .messageKey(messageKey)
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();
            outboxEventRepository.save(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, ex);
        }
    }
}
