package com.hotelbooking.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Postgres → Elasticsearch hotel reindex.
 * <p>
 * Requires Docker: Postgres + Elasticsearch (and Redis for Spring context).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HotelSearchSyncIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Test
    void reindex_populatesHotelsIndexFromPostgres() throws Exception {
        RestClient client = RestClient.create("http://localhost:" + port);

        String body = client.post()
                .uri("/api/admin/search/hotels/reindex")
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        assertThat(body).contains("\"indexed\":5");
        assertThat(body).contains("\"failures\":0");

        CountResponse count = elasticsearchClient.count(
                CountRequest.of(c -> c.index("hotels")));

        assertThat(count.count()).isEqualTo(5);
    }
}
