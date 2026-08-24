package com.hotelbooking.client;

import com.hotelbooking.exception.HotelCatalogUnavailableException;
import com.hotelbooking.exception.HotelNotFoundException;
import com.hotelbooking.exception.RoomNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sync catalog reads from hotel-service after DB-per-service split.
 * Booking validates room price/identity here; availability stays in hotel_booking.
 */
@Component
public class HotelCatalogClient {

    private final RestClient restClient;

    public HotelCatalogClient(@Value("${hotel.service.base-url:http://127.0.0.1:8086}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public RoomCatalogDto getRoom(Long roomId) {
        try {
            return restClient.get()
                    .uri("/internal/catalog/rooms/{roomId}", roomId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new RoomNotFoundException(roomId);
                    })
                    .body(RoomCatalogDto.class);
        } catch (RoomNotFoundException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new RoomNotFoundException(roomId);
            }
            throw new HotelCatalogUnavailableException("Hotel catalog rejected request: " + ex.getMessage(), ex);
        } catch (ResourceAccessException ex) {
            throw new HotelCatalogUnavailableException("Hotel catalog unreachable", ex);
        }
    }

    public HotelSummaryDto getHotelSummary(Long hotelId) {
        try {
            return restClient.get()
                    .uri("/internal/catalog/hotels/{hotelId}", hotelId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new HotelNotFoundException(hotelId);
                    })
                    .body(HotelSummaryDto.class);
        } catch (HotelNotFoundException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new HotelNotFoundException(hotelId);
            }
            throw new HotelCatalogUnavailableException("Hotel catalog rejected request: " + ex.getMessage(), ex);
        } catch (ResourceAccessException ex) {
            throw new HotelCatalogUnavailableException("Hotel catalog unreachable", ex);
        }
    }
}
