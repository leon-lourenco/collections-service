package com.cardbilling.collections.infrastructure.client;

import com.cardbilling.collections.domain.InterestCalculation;
import java.time.LocalDate;

/**
 * Body of {@code billing-service}'s {@code POST /invoices/{id}/interest} —
 * {@code {feeCents, dailyInterestCents, accrualDate}}, exactly as ARCHITECTURE.md specifies.
 *
 * <p>{@code accrualDate} is also the idempotency key on the receiving side: {@code billing-service}
 * treats a second call for the same {@code (invoiceId, accrualDate)} as a no-op. That is what makes
 * retrying this POST safe, which in turn is what makes it legitimate to wrap a non-GET call in a
 * retry at all.
 */
record ApplyInterestRequest(long feeCents, long dailyInterestCents, LocalDate accrualDate) {

    static ApplyInterestRequest from(InterestCalculation calculation) {
        return new ApplyInterestRequest(
                calculation.lateFee().cents(), calculation.dailyInterest().cents(), calculation.accrualDate());
    }
}
