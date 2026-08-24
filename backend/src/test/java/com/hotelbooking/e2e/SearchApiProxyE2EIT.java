package com.hotelbooking.e2e;

import com.hotelbooking.dto.response.HotelSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Public search APIs on the strangler still work after Search extraction
 * (proxy → search-service).
 * <p>
 * Requires search-service on {@code SEARCH_SERVICE_URL} (default :8083)
 * plus Docker Postgres + Elasticsearch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchApiProxyE2EIT {

    @LocalServerPort
    private int port;

    @Test
    void reindexAndSearch_viaStranglerProxy() {
        RestClient client = RestClient.create("http://localhost:" + port);

        String reindex = client.post()
                .uri("/api/admin/search/hotels/reindex")
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        assertThat(reindex).contains("\"indexed\":5");

        HotelSearchResponse search = client.get()
                .uri("/api/search/hotels?q=taj")
                .retrieve()
                .body(HotelSearchResponse.class);

        assertThat(search).isNotNull();
        assertThat(search.total()).isEqualTo(1);
        assertThat(search.hits().getFirst().name()).contains("Taj");
    }
}
