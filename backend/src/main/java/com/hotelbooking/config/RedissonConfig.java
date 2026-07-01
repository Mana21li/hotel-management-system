package com.hotelbooking.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires a {@link RedissonClient} that talks to the same Redis container we use for
 * caching ({@code docker-compose.yml} {@code redis} service).
 * <p>
 * We configure Redisson manually (instead of relying only on Spring Data Redis's
 * {@code LettuceConnectionFactory}) because distributed locks are a Redisson feature —
 * a higher-level API than raw {@code SET key NX EX}.
 */
@Configuration
public class RedissonConfig {

    /**
     * Creates the Redisson client bean.
     * <p>
     * {@code destroyMethod = "shutdown"} tells Spring to call {@code shutdown()} when
     * the app stops, closing Netty connections cleanly.
     *
     * @param host from {@code spring.data.redis.host} (same Redis as cache)
     * @param port from {@code spring.data.redis.port}
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {

        Config config = new Config();

        // Single-server mode = one Redis instance (our local Docker container).
        // For Sentinel/Cluster you would use useSentinelServers() / useClusterServers().
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);

        return Redisson.create(config);
    }
}
