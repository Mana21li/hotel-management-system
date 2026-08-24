package com.hotelbooking.hotel;

import com.hotelbooking.dto.response.HotelResponse;
import com.hotelbooking.exception.HotelNotFoundException;
import com.hotelbooking.exception.HotelServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Strangler-fig client: public hotel APIs stay on this process (:8080)
 * while catalog reads live in {@code hotel-service}.
 */
@Component
public class HotelServiceGateway {

    private final RestClient restClient;

    public HotelServiceGateway(
            @Value("${hotel.service.base-url:http://127.0.0.1:8086}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<HotelResponse> listActiveHotels() {
        try {
            List<HotelResponse> hotels = restClient.get()
                    .uri("/api/hotels")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return hotels != null ? hotels : List.of();
        } catch (RestClientResponseException | ResourceAccessException ex) {
            throw translate(ex);
        }
    }

    public HotelResponse getActiveHotelById(Long id) {
        try {
            return restClient.get()
                    .uri("/api/hotels/{id}", id)
                    .retrieve()
                    .body(HotelResponse.class);
        } catch (RestClientResponseException | ResourceAccessException ex) {
            throw translate(ex);
        }
    }

    private RuntimeException translate(RuntimeException ex) {
        if (ex instanceof ResourceAccessException access) {
            return HotelServiceUnavailableException.from(access);
        }
        if (ex instanceof RestClientResponseException http) {
            HttpStatusCode status = http.getStatusCode();
            if (status.value() == 404) {
                return new HotelNotFoundException(extractHotelId(http.getResponseBodyAsString()));
            }
            if (status.value() == 503) {
                return HotelServiceUnavailableException.from(http);
            }
        }
        return HotelServiceUnavailableException.from(ex);
    }

    private static Long extractHotelId(String body) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("id: (\\d+)").matcher(body);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return -1L;
    }
}
