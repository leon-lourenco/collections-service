package com.cardbilling.collections.infrastructure.cache;

import com.cardbilling.collections.domain.Money;
import com.cardbilling.collections.domain.OverdueInvoice;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The Redis payload for one date's overdue-invoice set, with the instant it was written.
 *
 * <p>It carries its own {@code cachedAt} rather than leaning on the key's remaining TTL because
 * the two windows are different questions: Redis's TTL decides how long the entry exists at all,
 * while {@code cachedAt} decides whether it is still fresh enough to serve without asking
 * {@code billing-service}. An entry can be past its freshness window and still be exactly what we
 * want when {@code billing-service} is down.
 *
 * <p>Deliberately its own type rather than reusing either the domain record or the HTTP DTO: a
 * cache format is a persisted format, and a field added to {@code billing-service}'s response
 * should not silently change how entries already in Redis deserialise.
 */
public record CachedOverdueInvoices(Instant cachedAt, List<CachedInvoice> invoices) {

    public record CachedInvoice(
            String invoiceId,
            String customerId,
            long totalAmountCents,
            String currency,
            LocalDate dueDate,
            LocalDate lastInterestAccrualDate) {

        static CachedInvoice from(OverdueInvoice invoice) {
            return new CachedInvoice(
                    invoice.invoiceId(),
                    invoice.customerId(),
                    invoice.totalAmount().cents(),
                    invoice.totalAmount().currencyCode(),
                    invoice.dueDate(),
                    invoice.lastInterestAccrualDate());
        }

        OverdueInvoice toDomain() {
            return new OverdueInvoice(
                    invoiceId,
                    customerId,
                    Money.ofCents(totalAmountCents, currency),
                    dueDate,
                    lastInterestAccrualDate);
        }
    }

    public static CachedOverdueInvoices of(List<OverdueInvoice> invoices, Instant cachedAt) {
        return new CachedOverdueInvoices(cachedAt, invoices.stream().map(CachedInvoice::from).toList());
    }

    public boolean isFreshAt(Instant now, Duration freshFor) {
        return !now.isAfter(cachedAt.plus(freshFor));
    }

    public List<OverdueInvoice> toDomain() {
        return invoices.stream().map(CachedInvoice::toDomain).toList();
    }
}
