package com.cardbilling.collections.infrastructure.cache;

import com.cardbilling.collections.domain.OverdueInvoice;
import com.cardbilling.collections.infrastructure.config.CollectionsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache-aside storage for {@code billing-service}'s overdue-invoice set, keyed
 * {@code billing:invoices:overdue:{date}} exactly as ARCHITECTURE.md specifies.
 *
 * <p>Every Redis operation here is best-effort. Redis is a cache, not a system of record, and a
 * dead Redis must degrade this service to "always calls billing-service" rather than take it
 * down — a cache outage that causes an outage is worse than no cache.
 */
@Component
public class OverdueInvoiceCache {

    static final String KEY_PREFIX = "billing:invoices:overdue:";

    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CollectionsProperties.Cache cacheProperties;

    public OverdueInvoiceCache(
            StringRedisTemplate redis,
            @Qualifier("cacheObjectMapper") ObjectMapper objectMapper,
            CollectionsProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.cacheProperties = properties.cache();
    }

    public static String keyFor(LocalDate asOf) {
        return KEY_PREFIX + asOf;
    }

    public Optional<CachedOverdueInvoices> read(LocalDate asOf) {
        try {
            String raw = redis.opsForValue().get(keyFor(asOf));
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, CachedOverdueInvoices.class));
        } catch (JsonProcessingException | RuntimeException e) {
            // Covers a Redis that is down (Spring's DataAccessException) and an entry that cannot
            // be deserialised. Both mean "no usable cache", which is a fact the caller handles,
            // not an error it should see.
            log.warn("Could not read the cached overdue-invoice set for {}", asOf, e);
            return Optional.empty();
        }
    }

    public void write(LocalDate asOf, List<OverdueInvoice> invoices, Instant now) {
        try {
            CachedOverdueInvoices payload = CachedOverdueInvoices.of(invoices, now);
            redis.opsForValue()
                    .set(keyFor(asOf), objectMapper.writeValueAsString(payload), cacheProperties.retainFor());
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Could not cache the overdue-invoice set for {}", asOf, e);
        }
    }
}
