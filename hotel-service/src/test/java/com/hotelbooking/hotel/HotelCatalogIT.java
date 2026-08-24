package com.hotelbooking.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HotelCatalogIT {

    @LocalServerPort
    private int port;

    @Test
    void listHotels_returnsSeededActiveHotels() {
        String body = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/hotels")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("The Taj Seaside");
        assertThat(body).contains("Capital Grand");
    }

    @Test
    void getHotel_returnsTaj() {
        String body = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/hotels/1")
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"id\":1");
        assertThat(body).contains("The Taj Seaside");
    }

    @Test
    void getHotel_missing_returns404() {
        assertThatThrownBy(() -> RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/hotels/99999")
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
