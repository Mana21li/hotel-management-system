package com.hotelbooking.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for {@code GET /api/search/hotels}.
 * <p>
 * Requires Docker: Postgres + Elasticsearch + Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HotelSearchIT {

    @LocalServerPort
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        client.post()
                .uri("/api/admin/search/hotels/reindex")
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void searchByHotelName_returnsMatchingHotel() {
        String body = client.get()
                .uri("/api/search/hotels?q=taj")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"total\":1");
        assertThat(body).contains("The Taj Seaside");
        assertThat(body).contains("\"cityName\":\"Mumbai\"");
    }

    @Test
    void searchByCityName_returnsHotelInThatCity() {
        String body = client.get()
                .uri("/api/search/hotels?q=delhi")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("Capital Grand");
        assertThat(body).contains("\"cityName\":\"Delhi\"");
    }

    @Test
    void searchWithoutQuery_returnsAllActiveHotels() {
        String body = client.get()
                .uri("/api/search/hotels")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"total\":5");
        assertThat(body).contains("\"page\":0");
        assertThat(body).contains("\"size\":20");
        assertThat(body).contains("The Taj Seaside");
        assertThat(body).contains("Bayview Suites");
    }

    @Test
    void filterByCityId_returnsOnlyHotelsInThatCity() {
        String body = client.get()
                .uri("/api/search/hotels?cityId=2")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"total\":1");
        assertThat(body).contains("Capital Grand");
        assertThat(body).contains("\"cityName\":\"Delhi\"");
    }

    @Test
    void filterByMinStars_returnsOnlyHighRatedHotels() {
        String body = client.get()
                .uri("/api/search/hotels?minStars=5")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"total\":2");
        assertThat(body).contains("The Taj Seaside");
        assertThat(body).contains("Manhattan Plaza");
    }

    @Test
    void filterByMaxPrice_excludesExpensiveHotels() {
        String body = client.get()
                .uri("/api/search/hotels?maxPrice=5000")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"total\":3");
        assertThat(body).contains("Garden City Inn");
        assertThat(body).doesNotContain("Manhattan Plaza");
        assertThat(body).doesNotContain("Bayview Suites");
    }

    @Test
    void sortByPriceAsc_returnsCheapestFirst() {
        String body = client.get()
                .uri("/api/search/hotels?sort=price_asc&size=1")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("Garden City Inn");
        assertThat(body).contains("\"minNightlyPrice\":2200.0");
    }

    @Test
    void pagination_returnsRequestedPage() {
        String page0 = client.get()
                .uri("/api/search/hotels?sort=price_asc&page=0&size=2")
                .retrieve()
                .body(String.class);
        String page1 = client.get()
                .uri("/api/search/hotels?sort=price_asc&page=1&size=2")
                .retrieve()
                .body(String.class);

        assertThat(page0).contains("\"total\":5");
        assertThat(page0).contains("\"page\":0");
        assertThat(page0).contains("\"size\":2");
        assertThat(page0).contains("Garden City Inn");

        assertThat(page1).contains("\"page\":1");
        assertThat(page1).contains("The Taj Seaside");
        assertThat(page1).doesNotContain("Garden City Inn");
    }

    @Test
    void fuzzySearch_toleratesTypoInHotelName() {
        String body = client.get()
                .uri("/api/search/hotels?q=tajj")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("The Taj Seaside");
    }

    @Test
    void fuzzySearch_toleratesTypoInCityName() {
        String body = client.get()
                .uri("/api/search/hotels?q=bangalor")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("Garden City Inn");
        assertThat(body).contains("\"cityName\":\"Bangalore\"");
    }

    @Test
    void fuzzySearch_toleratesMisspelledHotelName() {
        String body = client.get()
                .uri("/api/search/hotels?q=manhatan")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("Manhattan Plaza");
    }

    @Test
    void queryTooLong_returnsBadRequest() {
        String longQuery = "a".repeat(201);

        assertThatThrownBy(() -> client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/search/hotels")
                        .queryParam("q", longQuery)
                        .build())
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .hasMessageContaining("400");
    }
}
