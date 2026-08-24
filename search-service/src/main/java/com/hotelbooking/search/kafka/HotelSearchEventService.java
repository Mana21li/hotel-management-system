package com.hotelbooking.search.kafka;

import com.hotelbooking.search.client.HotelCatalogClient;
import com.hotelbooking.search.exception.HotelNotFoundException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class HotelSearchEventService {

    private final HotelCatalogClient hotelCatalogClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public HotelSearchEventService(
            HotelCatalogClient hotelCatalogClient,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.hotelCatalogClient = hotelCatalogClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishHotelUpsert(Long hotelId) {
        try {
            hotelCatalogClient.assertHotelExists(hotelId);
        } catch (HotelNotFoundException ex) {
            throw ex;
        }
        kafkaTemplate.send(KafkaTopics.HOTEL_EVENTS, String.valueOf(hotelId), HotelUpsertedEvent.of(hotelId));
    }
}
