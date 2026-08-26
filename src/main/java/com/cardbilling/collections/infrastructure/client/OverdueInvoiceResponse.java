package com.cardbilling.collections.infrastructure.client;

import com.cardbilling.collections.domain.Money;
import com.cardbilling.collections.domain.OverdueInvoice;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/**
 * One element of {@code billing-service}'s {@code GET /invoices/overdue?asOf={date}} response,
 * bound to that service's actual {@code InvoiceResponse} rather than to an assumption about it.
 *
 * <p>Only the six fields this service acts on are bound. {@code billing-service} also returns
 * {@code cardId}, {@code documentNumber}, {@code referenceMonth}, {@code closingDate},
 * {@code interestAppliedCents}, {@code amountPaidCents}, {@code amountDueCents} and
 * {@code status}; ignoring unknown properties explicitly means a field added on their side is a
 * non-event here instead of a deserialisation failure in the middle of a run.
 *
 * <p>Both amounts are carried. {@code totalAmountCents} is the cycle total the invoice closed
 * with, and the only correct base for a simple-interest charge. {@code amountOwedCents} is that
 * total plus interest already applied — {@code billing-service} computes it and this service reads
 * it rather than adding the two components back up itself.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OverdueInvoiceResponse(
        Long id,
        Long customerId,
        long totalAmountCents,
        long amountOwedCents,
        LocalDate dueDate,
        LocalDate lastInterestAccrualDate) {

    OverdueInvoice toDomain() {
        return new OverdueInvoice(
                id,
                customerId,
                Money.ofCents(totalAmountCents),
                Money.ofCents(amountOwedCents),
                dueDate,
                lastInterestAccrualDate);
    }
}
