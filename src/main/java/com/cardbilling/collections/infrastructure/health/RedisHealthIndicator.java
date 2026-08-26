package com.cardbilling.collections.infrastructure.health;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Redis cache connectivity.
 */
@Component
public class RedisHealthIndicator extends AbstractHealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            connectionFactory.getConnection().ping();
            builder.up().withDetail("redis", "connected");
        } catch (Exception ex) {
            builder.down().withException(ex);
        }
    }
}
