package com.cardbilling.collections.infrastructure.client;

import com.cardbilling.collections.application.port.OverdueInvoicePort;
import com.cardbilling.collections.domain.BillingServiceUnavailableException;
import com.cardbilling.collections.domain.Freshness;
import com.cardbilling.collections.domain.OverdueInvoice;
import com.cardbilling.collections.domain.OverdueInvoiceSnapshot;
import com.cardbilling.collections.infrastructure.cache.CachedOverdueInvoices;
import com.cardbilling.collections.infrastructure.cache.OverdueInvoiceCache;
import com.cardbilling.collections.infrastructure.config.CollectionsProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Cache-aside around {@code billing-service}'s overdue-invoice query, and the reason this service
 * keeps working when {@code billing-service} does not.
 *
 * <p>Three outcomes, in order:
 *
 * <ol>
 *   <li>A cached set written within {@code collections.cache.fresh-for} is served as-is —
 *       {@code billing-service} is not called at all.
 *   <li>Otherwise {@code billing-service} is called and the result cached. This is the normal path.
 *   <li>If that call fails, or its circuit breaker is open, the cached set is served <em>past</em>
 *       its freshness window rather than the run failing. It stops being available when Redis
 *       expires the key at {@code collections.cache.retain-for} — past that bound the data is old
 *       enough that acting on it would be worse than not running, and the run fails with
 *       {@link BillingServiceUnavailableException}.
 * </ol>
 *
 * <p>The stale entry is served, never rewritten: {@code cachedAt} keeps pointing at the last real
 * read from {@code billing-service}, so a long outage cannot quietly refresh its own way past the
 * retention bound.
 */
@Component
public class CachedOverdueInvoiceAdapter implements OverdueInvoicePort {

    private static final Logger log = LoggerFactory.getLogger(CachedOverdueInvoiceAdapter.class);

    private final BillingServiceClient billingServiceClient;
    private final OverdueInvoiceCache cache;
    private final CollectionsProperties.Cache cacheProperties;
    private final Clock clock;

    public CachedOverdueInvoiceAdapter(
            BillingServiceClient billingServiceClient,
            OverdueInvoiceCache cache,
            CollectionsProperties properties,
            Clock clock) {
        this.billingServiceClient = billingServiceClient;
        this.cache = cache;
        this.cacheProperties = properties.cache();
        this.clock = clock;
    }

    @Override
    public OverdueInvoiceSnapshot findOverdueAsOf(LocalDate asOf) {
        Instant now = clock.instant();
        Optional<CachedOverdueInvoices> cached = cache.read(asOf);

        if (cached.isPresent() && cached.get().isFreshAt(now, cacheProperties.freshFor())) {
            log.debug("Serving the overdue-invoice set for {} from a fresh cache entry", asOf);
            return new OverdueInvoiceSnapshot(cached.get().toDomain(), Freshness.CACHED_FRESH);
        }

        try {
            List<OverdueInvoice> live = billingServiceClient.fetchOverdueAsOf(asOf);
            cache.write(asOf, live, now);
            return new OverdueInvoiceSnapshot(live, Freshness.LIVE);
        } catch (CallNotPermittedException e) {
            // The breaker is open: billing-service is already known to be failing, so this call
            // was never attempted.
            return serveStaleOrFail(asOf, cached, e);
        } catch (RestClientException e) {
            // Connection refused, read timeout, or a 5xx that survived the retries.
            return serveStaleOrFail(asOf, cached, e);
        }
    }

    private OverdueInvoiceSnapshot serveStaleOrFail(
            LocalDate asOf, Optional<CachedOverdueInvoices> cached, RuntimeException cause) {
        return cached.map(entry -> {
                    log.warn(
                            "billing-service is unreachable ({}); serving the overdue-invoice set for {} "
                                    + "from a cache entry written at {}",
                            cause.getClass().getSimpleName(),
                            asOf,
                            entry.cachedAt());
                    return new OverdueInvoiceSnapshot(entry.toDomain(), Freshness.CACHED_STALE);
                })
                .orElseThrow(() -> new BillingServiceUnavailableException(asOf, cause));
    }
}
