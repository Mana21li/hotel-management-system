package com.hotelbooking.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.hotelbooking.search.document.HotelSearchDocument;
import com.hotelbooking.exception.SearchServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Full reindex: reads denormalized hotel rows from PostgreSQL and bulk-indexes them
 * into Elasticsearch via the {@code hotels} alias.
 * <p>
 * Postgres remains the source of truth; this service rebuilds the search read model.
 * Document {@code _id} = {@code hotelId} so re-running is idempotent (upsert).
 */
@Service
public class HotelSearchSyncService {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchSyncService.class);

    /**
     * Denormalized SQL: one row per hotel with city name and cheapest active room price.
     * We index <em>all</em> hotels (active and inactive); search filters on {@code active}.
     */
    private static final String INDEXING_SQL = """
      SELECT
          h.hotel_id,
          h.name,
          h.description,
          h.address_line,
          h.city_id,
          c.name AS city_name,
          h.star_rating,
          h.is_active,
          h.created_at,
          h.updated_at,
          COALESCE(MIN(r.nightly_price), 0) AS min_nightly_price
      FROM hotels h
      JOIN cities c ON c.city_id = h.city_id
      LEFT JOIN rooms r ON r.hotel_id = h.hotel_id AND r.is_active = TRUE
      GROUP BY
          h.hotel_id, h.name, h.description, h.address_line, h.city_id, c.name,
          h.star_rating, h.is_active, h.created_at, h.updated_at
      ORDER BY h.hotel_id
      """;

    private final JdbcTemplate jdbcTemplate;
    private final ElasticsearchClient elasticsearchClient;
    private final String hotelsIndexAlias;

    public HotelSearchSyncService(
            JdbcTemplate jdbcTemplate,
            ElasticsearchClient elasticsearchClient,
            @Value("${search.elasticsearch.hotels-index-alias:hotels}") String hotelsIndexAlias) {
        this.jdbcTemplate = jdbcTemplate;
        this.elasticsearchClient = elasticsearchClient;
        this.hotelsIndexAlias = hotelsIndexAlias;
    }

    /**
     * Reads every hotel from Postgres and bulk-indexes into Elasticsearch.
     *
     * @return summary counts for the admin API
     */
    public ReindexResult reindexAll() {
        List<HotelSearchDocument> documents = jdbcTemplate.query(INDEXING_SQL, this::mapRow);

        if (documents.isEmpty()) {
            log.warn("Reindex found 0 hotels in Postgres — nothing to index");
            return new ReindexResult(0, 0, 0);
        }

        BulkRequest.Builder bulk = new BulkRequest.Builder().refresh(Refresh.WaitFor);
        for (HotelSearchDocument doc : documents) {
            bulk.operations(op -> op.index(idx -> idx
                    .index(hotelsIndexAlias)
                    .id(String.valueOf(doc.hotelId()))
                    .document(doc)));
        }

        BulkResponse response;
        try {
            response = elasticsearchClient.bulk(bulk.build());
        } catch (Exception ex) {
            log.error("Elasticsearch bulk index failed", ex);
            throw SearchServiceUnavailableException.from(ex);
        }

        int failures = 0;
        if (response.errors()) {
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    failures++;
                    log.error("Bulk index error for id {}: {}", item.id(), item.error().reason());
                }
            }
        }

        int indexed = documents.size() - failures;
        log.info("Reindex complete: read={}, indexed={}, failures={}", documents.size(), indexed, failures);
        return new ReindexResult(documents.size(), indexed, failures);
    }

    private HotelSearchDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new HotelSearchDocument(
                rs.getLong("hotel_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("address_line"),
                rs.getLong("city_id"),
                rs.getString("city_name"),
                rs.getInt("star_rating"),
                rs.getDouble("min_nightly_price"),
                rs.getBoolean("is_active"),
                toIsoString(rs.getTimestamp("created_at")),
                toIsoString(rs.getTimestamp("updated_at"))
        );
    }

    private static String toIsoString(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant().toString() : null;
    }

    public record ReindexResult(int readFromPostgres, int indexed, int failures) {
    }
}
