package com.hotelbooking.search.client;

import com.hotelbooking.search.exception.HotelCatalogUnavailableException;
import com.hotelbooking.search.exception.HotelNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class HotelCatalogClient {

    private final RestClient restClient;

    public HotelCatalogClient(@Value("${hotel.service.base-url:http://127.0.0.1:8086}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public List<HotelSearchProjectionDto> listSearchProjections() {
        try {
            return restClient.get()
                    .uri("/internal/catalog/hotels/search-projections")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientResponseException | ResourceAccessException ex) {
            throw new HotelCatalogUnavailableException("Hotel catalog unreachable for search reindex", ex);
        }
    }

    public HotelSearchProjectionDto getSearchProjection(Long hotelId) {
        try {
            return restClient.get()
                    .uri("/internal/catalog/hotels/{hotelId}/search-projection", hotelId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new HotelNotFoundException(hotelId);
                    })
                    .body(HotelSearchProjectionDto.class);
        } catch (HotelNotFoundException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new HotelNotFoundException(hotelId);
            }
            throw new HotelCatalogUnavailableException("Hotel catalog rejected search projection", ex);
        } catch (ResourceAccessException ex) {
            throw new HotelCatalogUnavailableException("Hotel catalog unreachable", ex);
        }
    }

    public void assertHotelExists(Long hotelId) {
        try {
            restClient.get()
                    .uri("/internal/catalog/hotels/{hotelId}/exists", hotelId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new HotelNotFoundException(hotelId);
            }
            throw new HotelCatalogUnavailableException("Hotel catalog rejected exists check", ex);
        } catch (ResourceAccessException ex) {
            throw new HotelCatalogUnavailableException("Hotel catalog unreachable", ex);
        }
    }
}
