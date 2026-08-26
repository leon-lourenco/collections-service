package com.cardbilling.collections.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything about this service that changes between environments: where the two downstream
 * services live, how long a cached overdue-invoice set counts as fresh, and how long it is kept
 * around purely as a fallback.
 */
@ConfigurationProperties(prefix = "collections")
public record CollectionsProperties(Downstream billingService, Downstream notificationService, Cache cache) {

    /**
     * @param connectTimeout kept short on purpose: a downstream that is not answering should trip
     *     the circuit breaker quickly rather than hold a run open
     */
    public record Downstream(
            String baseUrl,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("5s") Duration readTimeout,
            @DefaultValue("collections-service") String clientRegistrationId) {}

    /**
     * @param freshFor how long a cached set is served without calling {@code billing-service} at
     *     all
     * @param retainFor how long the entry physically lives in Redis. Past {@code freshFor} but
     *     within this window it is only ever served as a fallback for an unreachable
     *     {@code billing-service}; past it, Redis has expired the key and a run with no reachable
     *     {@code billing-service} fails rather than acting on arbitrarily old data.
     */
    public record Cache(@DefaultValue("60s") Duration freshFor, @DefaultValue("10m") Duration retainFor) {

        public Cache {
            if (retainFor.compareTo(freshFor) < 0) {
                throw new IllegalArgumentException(
                        "collections.cache.retain-for (%s) must be at least collections.cache.fresh-for (%s), "
                                        .formatted(retainFor, freshFor)
                                + "otherwise the fallback window does not exist");
            }
        }
    }
}
