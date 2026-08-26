package com.cardbilling.collections.infrastructure.client;

import com.cardbilling.collections.domain.Money;
import com.cardbilling.collections.domain.OverdueInvoice;
import java.time.LocalDate;

/**
 * One element of {@code billing-service}'s {@code GET /invoices/overdue?asOf={date}} response.
 *
 * <p>{@code lastInterestAccrualDate} is the field this service cannot do its job without:
 * {@code POST /invoices/{id}/interest} takes {@code feeCents} and {@code dailyInterestCents}
 * separately, which means the caller has to know whether the one-off 2% late fee has already been
 * charged. ARCHITECTURE.md documents the interest endpoint but not the overdue payload's shape,
 * so this is the contract this service assumes and stubs against — worth confirming against
 * {@code billing-service}'s actual response before the first cross-service run.
 */
record OverdueInvoiceResponse(
        String invoiceId,
        String customerId,
        long totalAmountCents,
        String currency,
        LocalDate dueDate,
        LocalDate lastInterestAccrualDate) {

    OverdueInvoice toDomain() {
        return new OverdueInvoice(
                invoiceId,
                customerId,
                Money.ofCents(totalAmountCents, currency),
                dueDate,
                lastInterestAccrualDate);
    }
}
