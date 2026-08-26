package com.cardbilling.collections.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OverdueInvoiceTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 7, 26);

    @Test
    void counts_days_overdue_from_the_due_date() {
        OverdueInvoice invoice = invoiceLastAccruedOn(null);

        assertThat(invoice.daysOverdueAsOf(DUE_DATE)).isZero();
        assertThat(invoice.daysOverdueAsOf(DUE_DATE.plusDays(30))).isEqualTo(30L);
    }

    @Test
    @DisplayName("a null lastInterestAccrualDate is what marks the flat late fee as still owed")
    void treats_a_never_accrued_invoice_as_the_first_accrual() {
        assertThat(invoiceLastAccruedOn(null).firstAccrual()).isTrue();
        assertThat(invoiceLastAccruedOn(DUE_DATE.plusDays(1)).firstAccrual()).isFalse();
    }

    @Test
    void knows_whether_it_has_already_been_accrued_today() {
        LocalDate today = DUE_DATE.plusDays(10);
        OverdueInvoice invoice = invoiceLastAccruedOn(today);

        assertThat(invoice.alreadyAccruedOn(today)).isTrue();
        assertThat(invoice.alreadyAccruedOn(today.plusDays(1))).isFalse();
    }

    @Test
    void derives_its_interest_from_its_own_accrual_history() {
        LocalDate today = DUE_DATE.plusDays(10);

        InterestCalculation firstEver = invoiceLastAccruedOn(null).interestFor(today);
        InterestCalculation subsequent = invoiceLastAccruedOn(today.minusDays(1)).interestFor(today);

        assertThat(firstEver.lateFee().cents()).isEqualTo(2_000L); // 2% of 100000
        assertThat(subsequent.lateFee().isZero()).isTrue();
        assertThat(subsequent.dailyInterest().cents()).isEqualTo(1_000L); // 1% of 100000
    }

    @Test
    @DisplayName("interest is charged on the cycle total, never on billing-service's amountOwed")
    void never_accrues_on_an_amount_that_already_includes_interest() {
        // This invoice closed at 100000 and has already had 37500 in interest applied, so
        // billing-service reports 137500 owed. Charging 1% of the amount owed would be
        // compounding; the ported rule is simple interest on the original total.
        OverdueInvoice partlyAccrued = new OverdueInvoice(
                1L,
                7L,
                Money.ofCents(100_000L),
                Money.ofCents(137_500L),
                DUE_DATE,
                DUE_DATE.plusDays(9));

        InterestCalculation calculation = partlyAccrued.interestFor(DUE_DATE.plusDays(10));

        assertThat(calculation.dailyInterest().cents()).isEqualTo(1_000L); // 1% of 100000
        assertThat(calculation.dailyInterest().cents()).isNotEqualTo(1_375L); // ...not 1% of 137500
        assertThat(partlyAccrued.amountOwed().cents()).isEqualTo(137_500L);
    }

    private OverdueInvoice invoiceLastAccruedOn(LocalDate lastInterestAccrualDate) {
        return new OverdueInvoice(
                1L,
                7L,
                Money.ofCents(100_000L),
                Money.ofCents(100_000L),
                DUE_DATE,
                lastInterestAccrualDate);
    }
}
