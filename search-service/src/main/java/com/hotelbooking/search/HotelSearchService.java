package com.hotelbooking.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.hotelbooking.search.dto.HotelSearchHit;
import com.hotelbooking.search.dto.HotelSearchResponse;
import com.hotelbooking.search.exception.SearchServiceUnavailableException;
import com.hotelbooking.search.document.HotelSearchDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Queries the Elasticsearch {@code hotels} read model.
 * <p>
 * Postgres is never hit on the search path — ES is the query engine for discovery.
 */
@Service
public class HotelSearchService {

    private static final Logger log = LoggerFactory.getLogger(HotelSearchService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final float EXACT_MATCH_BOOST = 2.0f;

    /** Field boosts: hotel name matters most, then city. */
    private static final List<String> SEARCH_FIELDS = List.of(
            "name^3",
            "cityName^2",
            "description",
            "addressLine"
    );

    private final ElasticsearchClient elasticsearchClient;
    private final String hotelsIndexAlias;
    private final boolean fuzzyEnabled;
    private final int maxQueryLength;

    public HotelSearchService(
            ElasticsearchClient elasticsearchClient,
            @Value("${search.elasticsearch.hotels-index-alias:hotels}") String hotelsIndexAlias,
            @Value("${search.elasticsearch.fuzzy-enabled:true}") boolean fuzzyEnabled,
            @Value("${search.elasticsearch.max-query-length:200}") int maxQueryLength) {
        this.elasticsearchClient = elasticsearchClient;
        this.hotelsIndexAlias = hotelsIndexAlias;
        this.fuzzyEnabled = fuzzyEnabled;
        this.maxQueryLength = maxQueryLength;
    }

    /**
     * Full-text search over active hotels with optional filters, sort, and pagination.
     */
    public HotelSearchResponse search(HotelSearchCriteria criteria) {
        String q = criteria.query() != null ? criteria.query().trim() : "";
        validateQueryLength(q);

        int size = clampSize(criteria.size());
        int page = Math.max(criteria.page(), 0);
        int from = page * size;

        log.debug("Hotel search: q='{}', page={}, size={}, sort={}", q, page, size, criteria.sort());

        SearchResponse<HotelSearchDocument> response;
        try {
            response = elasticsearchClient.search(s -> {
                s.index(hotelsIndexAlias);
                s.from(from);
                s.size(size);
                s.query(qb -> qb.bool(b -> {
                    applyFilters(b, criteria);
                    if (q.isEmpty()) {
                        b.must(m -> m.matchAll(ma -> ma));
                    } else {
                        addTextQuery(b, q);
                    }
                    return b;
                }));
                applySort(s, criteria.sort(), q);
                return s;
            }, HotelSearchDocument.class);
        } catch (Exception ex) {
            log.warn("Elasticsearch search failed for q='{}': {}", q, ex.toString());
            throw SearchServiceUnavailableException.from(ex);
        }

        List<HotelSearchHit> hits = response.hits().hits().stream()
                .map(this::toHit)
                .toList();

        long total = response.hits().total() != null
                ? response.hits().total().value()
                : hits.size();

        return new HotelSearchResponse(q, total, page, size, hits);
    }

    /** Backward-compatible entry point used by earlier milestones. */
    public HotelSearchResponse search(String query) {
        return search(new HotelSearchCriteria(query, null, null, null, HotelSearchSort.RELEVANCE, 0, DEFAULT_PAGE_SIZE));
    }

    private void validateQueryLength(String query) {
        if (query.length() > maxQueryLength) {
            throw new IllegalArgumentException(
                    "Query exceeds maximum length of " + maxQueryLength + " characters");
        }
    }

    private static void applyFilters(BoolQuery.Builder builder, HotelSearchCriteria criteria) {
        builder.filter(f -> f.term(t -> t.field("active").value(true)));
        if (criteria.cityId() != null) {
            builder.filter(f -> f.term(t -> t.field("cityId").value(criteria.cityId())));
        }
        if (criteria.minStars() != null) {
            builder.filter(f -> f.range(r -> r.number(n -> n
                    .field("starRating")
                    .gte(criteria.minStars().doubleValue()))));
        }
        if (criteria.maxPrice() != null) {
            builder.filter(f -> f.range(r -> r.number(n -> n
                    .field("minNightlyPrice")
                    .lte(criteria.maxPrice()))));
        }
    }

    /**
     * Exact match is boosted; fuzzy match ({@code AUTO} edit distance) catches typos
     * with lower score so correct spellings rank first.
     */
    private void addTextQuery(BoolQuery.Builder builder, String query) {
        if (fuzzyEnabled) {
            builder.must(m -> m.bool(bb -> {
                bb.should(s -> s.multiMatch(mm -> mm
                        .query(query)
                        .fields(SEARCH_FIELDS)
                        .type(TextQueryType.BestFields)
                        .boost(EXACT_MATCH_BOOST)));
                bb.should(s -> s.multiMatch(mm -> mm
                        .query(query)
                        .fields(SEARCH_FIELDS)
                        .type(TextQueryType.BestFields)
                        .fuzziness("AUTO")));
                bb.minimumShouldMatch("1");
                return bb;
            }));
        } else {
            builder.must(m -> m.multiMatch(mm -> mm
                    .query(query)
                    .fields(SEARCH_FIELDS)
                    .type(TextQueryType.BestFields)));
        }
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static void applySort(
            co.elastic.clients.elasticsearch.core.SearchRequest.Builder builder,
            HotelSearchSort sort,
            String query) {
        switch (sort) {
            case PRICE_ASC -> builder.sort(so -> so.field(f -> f.field("minNightlyPrice").order(SortOrder.Asc)));
            case PRICE_DESC -> builder.sort(so -> so.field(f -> f.field("minNightlyPrice").order(SortOrder.Desc)));
            case STARS_ASC -> builder.sort(so -> so.field(f -> f.field("starRating").order(SortOrder.Asc)));
            case STARS_DESC -> builder.sort(so -> so.field(f -> f.field("starRating").order(SortOrder.Desc)));
            case RELEVANCE -> {
                if (!query.isEmpty()) {
                    builder.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)));
                } else {
                    builder.sort(so -> so.field(f -> f.field("starRating").order(SortOrder.Desc)));
                }
            }
        }
        builder.sort(so -> so.field(f -> f.field("hotelId").order(SortOrder.Asc)));
    }

    private HotelSearchHit toHit(Hit<HotelSearchDocument> hit) {
        HotelSearchDocument doc = hit.source();
        if (doc == null) {
            throw new IllegalStateException("Elasticsearch hit missing _source for id " + hit.id());
        }
        return new HotelSearchHit(
                doc.hotelId(),
                doc.name(),
                doc.description(),
                doc.addressLine(),
                doc.cityName(),
                doc.starRating(),
                doc.minNightlyPrice()
        );
    }
}
