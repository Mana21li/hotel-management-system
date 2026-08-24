package com.hotelbooking.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Central Redis caching configuration.
 * <p>
 * {@code @EnableCaching} turns on Spring's caching annotation support (a BeanPostProcessor
 * that wraps {@code @Cacheable}/{@code @CacheEvict} methods in a caching proxy). Without
 * it, those annotations are silently ignored.
 * <p>
 * We override Spring Boot's default {@code RedisCacheManager} settings so that:
 * <ul>
 *   <li>keys are readable strings ({@code hotelById::1}) instead of binary;</li>
 *   <li>values are JSON instead of JDK-serialized blobs (readable, robust, language-neutral);</li>
 *   <li>every entry has a default TTL — the time-based backstop against stale data;</li>
 *   <li>{@code null} results are never cached.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /** Cache name (key namespace) for single-hotel lookups: {@code hotelById::<id>}. */
    public static final String HOTEL_BY_ID = "hotelById";

    /** Cache name for the active-hotels list: {@code hotelList::all}. */
    public static final String HOTEL_LIST = "hotelList";

    /**
     * The default template applied to every cache. Spring Boot detects this bean and
     * uses it to build the auto-configured {@link org.springframework.data.redis.cache.RedisCacheManager}.
     */
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        // Use the no-arg constructor on purpose: its internal ObjectMapper enables
        // default typing, so each cached value carries an "@class" hint. Without that
        // hint, reads come back as a generic LinkedHashMap and the cache cast fails.
        // (Do NOT pass in the plain Spring Boot ObjectMapper here — it has no typing.)
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        return RedisCacheConfiguration.defaultCacheConfig()
                // TTL backstop: entries auto-expire after 10 minutes even if we never evict.
                .entryTtl(Duration.ofMinutes(10))
                // Don't store null results (so a "not found" never poisons the cache).
                .disableCachingNullValues()
                // Human-readable string keys instead of the default binary keys.
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // JSON values instead of JDK serialization.
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));
    }
}
