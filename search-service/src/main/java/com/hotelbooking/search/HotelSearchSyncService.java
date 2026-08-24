package com.hotelbooking.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.hotelbooking.search.client.HotelCatalogClient;
import com.hotelbooking.search.client.HotelSearchProjectionDto;
import com.hotelbooking.search.document.HotelSearchDocument;
import com.hotelbooking.search.exception.SearchServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Full reindex: reads denormalized hotel rows from hotel-service and bulk-indexes
 * them into Elasticsearch. Postgres catalog truth lives only in hotel_catalog.
 */
@Service
public class HotelSearchSyncService {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchSyncService.class);

    private final HotelCatalogClient hotelCatalogClient;
    private final ElasticsearchClient elasticsearchClient;
    private final String hotelsIndexAlias;

    public HotelSearchSyncService(
            HotelCatalogClient hotelCatalogClient,
            ElasticsearchClient elasticsearchClient,
            @Value("${search.elasticsearch.hotels-index-alias:hotels}") String hotelsIndexAlias) {
        this.hotelCatalogClient = hotelCatalogClient;
        this.elasticsearchClient = elasticsearchClient;
        this.hotelsIndexAlias = hotelsIndexAlias;
    }

    public ReindexResult reindexAll() {
        List<HotelSearchDocument> documents = hotelCatalogClient.listSearchProjections().stream()
                .map(this::toDocument)
                .toList();

        if (documents.isEmpty()) {
            log.warn("Reindex found 0 hotels from catalog — nothing to index");
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

    public void indexHotelById(Long hotelId) {
        HotelSearchDocument doc = toDocument(hotelCatalogClient.getSearchProjection(hotelId));
        try {
            elasticsearchClient.index(i -> i
                    .index(hotelsIndexAlias)
                    .id(String.valueOf(doc.hotelId()))
                    .document(doc)
                    .refresh(Refresh.WaitFor));
            log.info("Indexed hotelId={} into alias={}", hotelId, hotelsIndexAlias);
        } catch (Exception ex) {
            log.error("Elasticsearch index failed for hotelId={}", hotelId, ex);
            throw SearchServiceUnavailableException.from(ex);
        }
    }

    private HotelSearchDocument toDocument(HotelSearchProjectionDto projection) {
        return new HotelSearchDocument(
                projection.hotelId(),
                projection.name(),
                projection.description(),
                projection.addressLine(),
                projection.cityId(),
                projection.cityName(),
                projection.starRating(),
                projection.minNightlyPrice(),
                projection.active(),
                projection.createdAt(),
                projection.updatedAt());
    }

    public record ReindexResult(int readFromPostgres, int indexed, int failures) {
    }
}
