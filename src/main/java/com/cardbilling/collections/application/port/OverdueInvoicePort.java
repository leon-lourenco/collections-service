package com.cardbilling.collections.application.port;

import com.cardbilling.collections.domain.BillingServiceUnavailableException;
import com.cardbilling.collections.domain.OverdueInvoiceSnapshot;
import java.time.LocalDate;

/**
 * Reads the set of invoices past due as of a given date. The only implementation talks to
 * {@code billing-service} through a Redis cache — but that is an infrastructure concern; all the
 * use case is promised is a snapshot that knows how fresh it is.
 */
public interface OverdueInvoicePort {

    /**
     * @throws BillingServiceUnavailableException if the set cannot be produced from either
     *     {@code billing-service} or the cache
     */
    OverdueInvoiceSnapshot findOverdueAsOf(LocalDate asOf);
}
