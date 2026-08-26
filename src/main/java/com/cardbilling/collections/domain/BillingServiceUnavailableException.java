package com.cardbilling.collections.domain;

import java.time.LocalDate;

/**
 * Raised when the overdue-invoice set for a run cannot be produced at all: {@code billing-service}
 * is unreachable (or its circuit breaker is open) <em>and</em> there is nothing left in the cache
 * to fall back on.
 *
 * <p>This is deliberately the only path that fails a run. A cache hit past its freshness window
 * is a degraded run, not a failed one — see {@link Freshness#CACHED_STALE}.
 */
public class BillingServiceUnavailableException extends RuntimeException {

    private final LocalDate asOf;

    public BillingServiceUnavailableException(LocalDate asOf, Throwable cause) {
        super(
                "billing-service is unreachable and no cached overdue-invoice set is available for %s"
                        .formatted(asOf),
                cause);
        this.asOf = asOf;
    }

    public LocalDate asOf() {
        return asOf;
    }
}
