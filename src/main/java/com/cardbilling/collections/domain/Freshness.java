package com.cardbilling.collections.domain;

/**
 * Where the overdue-invoice set a collections run acted on actually came from. A run that worked
 * off a stale cache because {@code billing-service} was unreachable is still a successful run,
 * but it is not the same run — this is what lets the caller (and the evidence capture) tell the
 * difference instead of guessing.
 */
public enum Freshness {

    /** Read live from {@code billing-service} on this run. */
    LIVE,

    /** Served from Redis within the normal cache window — {@code billing-service} was not called. */
    CACHED_FRESH,

    /**
     * Served from Redis past the normal cache window because {@code billing-service} could not be
     * reached or its circuit breaker was open. The run is degraded, not failed.
     */
    CACHED_STALE
}
