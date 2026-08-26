package com.cardbilling.collections.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What an overdue invoice owes for one day of being overdue: a flat 2% late fee the first time it
 * goes overdue, plus 1% simple daily interest every day after that. Ported unchanged in meaning
 * from {@code card-billing-legacy}'s {@code InterestAccrualJob} — the same "multa + mora diária"
 * rule a Brazilian card issuer typically applies.
 *
 * <p>Interest is simple, not compounding: every day's 1% is taken on the invoice's original
 * total, never on interest already applied. That is why this type takes the invoice total rather
 * than a running balance.
 *
 * <p>Fee and daily interest stay separate all the way to {@code billing-service}'s
 * {@code POST /invoices/{id}/interest} rather than being summed here, because they are two
 * different charges and the invoice needs to be able to say which is which.
 */
public record InterestCalculation(Money lateFee, Money dailyInterest, LocalDate accrualDate) {

    private static final BigDecimal LATE_FEE_RATE = new BigDecimal("0.02");
    private static final BigDecimal DAILY_INTEREST_RATE = new BigDecimal("0.01");

    public InterestCalculation {
        Objects.requireNonNull(lateFee, "lateFee must not be null");
        Objects.requireNonNull(dailyInterest, "dailyInterest must not be null");
        Objects.requireNonNull(accrualDate, "accrualDate must not be null");
    }

    /**
     * @param invoiceTotal the invoice's original total, not its current balance
     * @param firstAccrual whether this is the first day interest has ever been accrued on this
     *     invoice — the flat late fee is charged once, on that first day only
     */
    public static InterestCalculation forOverdueInvoice(
            Money invoiceTotal, boolean firstAccrual, LocalDate accrualDate) {
        Objects.requireNonNull(invoiceTotal, "invoiceTotal must not be null");
        Money lateFee = firstAccrual
                ? invoiceTotal.percentage(LATE_FEE_RATE)
                : new Money(0L, invoiceTotal.currency());
        return new InterestCalculation(lateFee, invoiceTotal.percentage(DAILY_INTEREST_RATE), accrualDate);
    }

    public Money total() {
        return lateFee.plus(dailyInterest);
    }
}
