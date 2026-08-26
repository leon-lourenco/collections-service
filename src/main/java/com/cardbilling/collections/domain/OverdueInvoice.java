package com.cardbilling.collections.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * An invoice {@code billing-service} reports as past its due date and not yet paid. This service
 * never owns or mutates one — it reads this snapshot, decides what should happen to it, and asks
 * {@code billing-service} to make it happen.
 *
 * @param lastInterestAccrualDate the last day interest was applied, or {@code null} if it never
 *     has been. This is what tells us whether the flat late fee is still owed, so
 *     {@code billing-service}'s overdue payload has to carry it.
 */
public record OverdueInvoice(
        String invoiceId,
        String customerId,
        Money totalAmount,
        LocalDate dueDate,
        LocalDate lastInterestAccrualDate) {

    public OverdueInvoice {
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        Objects.requireNonNull(dueDate, "dueDate must not be null");
    }

    public long daysOverdueAsOf(LocalDate asOf) {
        return ChronoUnit.DAYS.between(dueDate, asOf);
    }

    /** True when the flat 2% late fee has not been charged yet. */
    public boolean firstAccrual() {
        return lastInterestAccrualDate == null;
    }

    public boolean alreadyAccruedOn(LocalDate date) {
        return date.equals(lastInterestAccrualDate);
    }

    public InterestCalculation interestFor(LocalDate accrualDate) {
        return InterestCalculation.forOverdueInvoice(totalAmount, firstAccrual(), accrualDate);
    }
}
