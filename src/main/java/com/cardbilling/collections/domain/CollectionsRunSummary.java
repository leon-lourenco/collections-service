package com.cardbilling.collections.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * What one collections run actually did. The legacy's two jobs each returned a bare {@code int}
 * ("how many did you touch"), which was enough when a human was reading a log line and nothing
 * else. A run that quietly skipped half its invoices because {@code notification-service} was
 * down needs to say so, so every outcome gets its own count.
 */
public record CollectionsRunSummary(
        LocalDate asOf,
        Freshness freshness,
        int overdueInvoices,
        int interestApplied,
        int interestAlreadyAccrued,
        int notificationsRequested,
        int belowFirstEscalationStage,
        int failures) {

    public CollectionsRunSummary {
        Objects.requireNonNull(asOf, "asOf must not be null");
        Objects.requireNonNull(freshness, "freshness must not be null");
    }

    /** True when this run acted on a cached set because {@code billing-service} was unreachable. */
    public boolean degraded() {
        return freshness == Freshness.CACHED_STALE;
    }

    public static Builder builder(LocalDate asOf, Freshness freshness, int overdueInvoices) {
        return new Builder(asOf, freshness, overdueInvoices);
    }

    /** Mutable tally used while a run is in progress; {@link #build()} freezes it. */
    public static final class Builder {

        private final LocalDate asOf;
        private final Freshness freshness;
        private final int overdueInvoices;
        private int interestApplied;
        private int interestAlreadyAccrued;
        private int notificationsRequested;
        private int belowFirstEscalationStage;
        private int failures;

        private Builder(LocalDate asOf, Freshness freshness, int overdueInvoices) {
            this.asOf = asOf;
            this.freshness = freshness;
            this.overdueInvoices = overdueInvoices;
        }

        public Builder interestApplied() {
            interestApplied++;
            return this;
        }

        public Builder interestAlreadyAccrued() {
            interestAlreadyAccrued++;
            return this;
        }

        public Builder notificationRequested() {
            notificationsRequested++;
            return this;
        }

        public Builder belowFirstEscalationStage() {
            belowFirstEscalationStage++;
            return this;
        }

        public Builder failure() {
            failures++;
            return this;
        }

        public CollectionsRunSummary build() {
            return new CollectionsRunSummary(
                    asOf,
                    freshness,
                    overdueInvoices,
                    interestApplied,
                    interestAlreadyAccrued,
                    notificationsRequested,
                    belowFirstEscalationStage,
                    failures);
        }
    }
}
