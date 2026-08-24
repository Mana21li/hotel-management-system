package com.hotelbooking.search;

import com.hotelbooking.dto.response.HotelSearchResponse;
import com.hotelbooking.dto.response.ReindexResponse;
import com.hotelbooking.exception.HotelNotFoundException;
import com.hotelbooking.exception.SearchServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.Optional;

/**
 * Strangler-fig client: public search APIs stay on this process (:8080)
 * while Elasticsearch lives in {@code search-service}.
 */
@Component
public class SearchServiceGateway {

    private final RestClient restClient;

    public SearchServiceGateway(
            @Value("${search.service.base-url:http://127.0.0.1:8083}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public HotelSearchResponse search(
            String query,
            Long cityId,
            Integer minStars,
            Double maxPrice,
            String sort,
            int page,
            int size) {
        try {
            return restClient.get()
                    .uri(uri -> uri.path("/api/search/hotels")
                            .queryParamIfPresent("q", Optional.ofNullable(query))
                            .queryParamIfPresent("cityId", Optional.ofNullable(cityId))
                            .queryParamIfPresent("minStars", Optional.ofNullable(minStars))
                            .queryParamIfPresent("maxPrice", Optional.ofNullable(maxPrice))
                            .queryParam("sort", sort)
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(HotelSearchResponse.class);
        } catch (RestClientResponseException | ResourceAccessException ex) {
            throw translate(ex);
        }
    }

    public ReindexResponse reindex() {
        try {
            return restClient.post()
                    .uri("/api/admin/search/hotels/reindex")
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ReindexResponse.class);
        } catch (RestClientResponseException | ResourceAccessException ex) {
            throw translate(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> syncHotel(Long hotelId) {
        try {
            return restClient.post()
                    .uri("/api/admin/search/hotels/{id}/sync", hotelId)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException | ResourceAccessException ex) {
            throw translate(ex);
        }
    }

    private RuntimeException translate(RuntimeException ex) {
        if (ex instanceof ResourceAccessException access) {
            return SearchServiceUnavailableException.from(access);
        }
        if (ex instanceof RestClientResponseException http) {
            HttpStatusCode status = http.getStatusCode();
            String body = http.getResponseBodyAsString();
            if (status.value() == 404) {
                return new HotelNotFoundException(extractHotelId(body));
            }
            if (status.value() == 400) {
                return new IllegalArgumentException(http.getStatusText() + ": " + body);
            }
            if (status.value() == 503) {
                return SearchServiceUnavailableException.from(http);
            }
        }
        return SearchServiceUnavailableException.from(ex);
    }

    private static Long extractHotelId(String body) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("id: (\\d+)").matcher(body);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return -1L;
    }
}
