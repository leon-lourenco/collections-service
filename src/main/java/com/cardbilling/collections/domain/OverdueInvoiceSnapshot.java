package com.cardbilling.collections.domain;

import java.util.List;
import java.util.Objects;

/**
 * The overdue-invoice set a run works from, together with where it came from. Bundling the two
 * keeps {@link Freshness} out of a side channel: a caller cannot read the invoices without also
 * being handed the fact that they might be stale.
 */
public record OverdueInvoiceSnapshot(List<OverdueInvoice> invoices, Freshness freshness) {

    public OverdueInvoiceSnapshot {
        Objects.requireNonNull(freshness, "freshness must not be null");
        invoices = List.copyOf(Objects.requireNonNull(invoices, "invoices must not be null"));
    }

    public static OverdueInvoiceSnapshot live(List<OverdueInvoice> invoices) {
        return new OverdueInvoiceSnapshot(invoices, Freshness.LIVE);
    }

    public int size() {
        return invoices.size();
    }
}
