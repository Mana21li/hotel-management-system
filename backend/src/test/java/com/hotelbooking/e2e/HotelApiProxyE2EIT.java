package com.hotelbooking.e2e;

import com.hotelbooking.dto.response.HotelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Public hotel APIs on the strangler still work after Hotel extraction
 * (proxy → hotel-service).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HotelApiProxyE2EIT {

    @LocalServerPort
    private int port;

    @Test
    void listAndGetHotel_viaStranglerProxy() {
        RestClient client = RestClient.create("http://localhost:" + port);

        List<HotelResponse> hotels = client.get()
                .uri("/api/hotels")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertThat(hotels).isNotEmpty();
        assertThat(hotels.stream().map(HotelResponse::name)).contains("The Taj Seaside");

        HotelResponse taj = client.get()
                .uri("/api/hotels/1")
                .retrieve()
                .body(HotelResponse.class);
        assertThat(taj).isNotNull();
        assertThat(taj.name()).contains("Taj");
    }
}
