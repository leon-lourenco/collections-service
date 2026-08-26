package com.cardbilling.collections.infrastructure.health;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Redis cache connectivity.
 *
 * <p>Note that Redis being down does not make this service unhealthy in any meaningful sense —
 * the cache is a fallback, and {@code OverdueInvoiceCache} degrades to always calling
 * {@code billing-service} when it is unreachable. This reports the dependency, it does not gate
 * the service on it.
 */
@Component
public class RedisHealthIndicator extends AbstractHealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        // try-with-resources: a health endpoint is polled continuously, so a connection left open
        // per check drains the pool rather than merely leaking once.
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.ping();
            builder.up().withDetail("redis", "connected");
        } catch (Exception ex) {
            builder.down().withException(ex);
        }
    }
}
