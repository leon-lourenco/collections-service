package com.cardbilling.collections.infrastructure.cache;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis wiring for the one thing this service caches.
 *
 * <p>The cache gets its own {@link ObjectMapper} rather than the auto-configured one the web layer
 * uses. They serialise different things for different reasons — a change to how the API renders
 * dates should not silently make every entry already sitting in Redis unreadable — and
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} being off here specifically means an older cache entry
 * written before a field was added still deserialises instead of poisoning the fallback path at
 * exactly the moment it is needed.
 *
 * <p>The connection factory itself is Boot's, configured through {@code spring.data.redis.*}.
 */
@Configuration
public class RedisCacheConfig {

    @Bean("cacheObjectMapper")
    public ObjectMapper cacheObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
