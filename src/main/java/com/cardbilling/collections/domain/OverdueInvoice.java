package com.cardbilling.collections.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * An invoice {@code billing-service} reports as past its due date and not yet paid. This service
 * never owns or mutates one — it reads this snapshot, decides what should happen to it, and asks
 * {@code billing-service} to make it happen.
 *
 * <p>Identifiers are {@code long} because {@code billing-service} generates them from a database
 * identity column and {@code notification-service} takes them the same way. Carrying them as
 * strings here would mean two conversions per invoice and a class of mismatch that only shows up
 * at runtime.
 *
 * @param totalAmount the cycle total the invoice closed with, and the base every interest charge
 *     is calculated from. Not {@link #amountOwed()} — the legacy's rule is simple interest, so
 *     each day's 1% is taken on the original total and never on interest already applied.
 * @param amountOwed total plus interest charged so far, as {@code billing-service} computes it.
 *     Carried through rather than derived here: {@code billing-service} owns that sum, and two
 *     services independently adding up the same figure is how they end up disagreeing.
 * @param lastInterestAccrualDate the last day interest was applied, or {@code null} if it never
 *     has been. This is what tells us whether the flat late fee is still owed.
 */
public record OverdueInvoice(
        long invoiceId,
        long customerId,
        Money totalAmount,
        Money amountOwed,
        LocalDate dueDate,
        LocalDate lastInterestAccrualDate) {

    public OverdueInvoice {
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        Objects.requireNonNull(amountOwed, "amountOwed must not be null");
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
