package com.cardbilling.collections.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The 2%-once-plus-1%-daily rule, ported from {@code card-billing-legacy}'s
 * {@code InterestAccrualJob}. Every expected value below is the legacy formula worked through by
 * hand — {@code Math.round(totalAmountCents * 0.02)} and {@code Math.round(totalAmountCents *
 * 0.01)} — so this is a regression test against the legacy's behaviour, not against this
 * implementation's.
 */
class InterestCalculationTest {

    private static final LocalDate ACCRUAL_DATE = LocalDate.of(2026, 8, 25);

    @Test
    @DisplayName("first day overdue: flat 2% late fee plus 1% daily interest")
    void charges_the_late_fee_once_on_the_first_accrual() {
        InterestCalculation calculation =
                InterestCalculation.forOverdueInvoice(Money.ofCents(125_000L, "BRL"), true, ACCRUAL_DATE);

        assertThat(calculation.lateFee().cents()).isEqualTo(2_500L);
        assertThat(calculation.dailyInterest().cents()).isEqualTo(1_250L);
        assertThat(calculation.total().cents()).isEqualTo(3_750L);
        assertThat(calculation.accrualDate()).isEqualTo(ACCRUAL_DATE);
    }

    @Test
    @DisplayName("every day after the first: 1% daily interest only, no second late fee")
    void charges_no_further_late_fee_after_the_first_accrual() {
        InterestCalculation calculation =
                InterestCalculation.forOverdueInvoice(Money.ofCents(125_000L, "BRL"), false, ACCRUAL_DATE);

        assertThat(calculation.lateFee().isZero()).isTrue();
        assertThat(calculation.dailyInterest().cents()).isEqualTo(1_250L);
        assertThat(calculation.total().cents()).isEqualTo(1_250L);
    }

    @Test
    @DisplayName("interest is simple, not compounding: it is always taken on the original total")
    void accrues_on_the_original_total_every_day() {
        Money originalTotal = Money.ofCents(100_000L, "BRL");

        long dayOne = InterestCalculation.forOverdueInvoice(originalTotal, true, ACCRUAL_DATE)
                .dailyInterest()
                .cents();
        long dayThirty = InterestCalculation.forOverdueInvoice(originalTotal, false, ACCRUAL_DATE.plusDays(29))
                .dailyInterest()
                .cents();

        // A compounding rule would have grown day thirty's charge; a simple one never does.
        assertThat(dayThirty).isEqualTo(dayOne).isEqualTo(1_000L);
    }

    @ParameterizedTest(name = "{0} cents -> fee {1}, daily {2}")
    @CsvSource({
        // Worked through the legacy's Math.round(total * rate) by hand.
        "12345, 247, 123", // 246.9 -> 247, 123.45 -> 123
        "1, 0, 0", // 0.02 -> 0, 0.01 -> 0
        "25, 1, 0", // 0.5 -> 1 (half up), 0.25 -> 0
        "50, 1, 1", // 1.0 -> 1, 0.5 -> 1 (half up)
        "99, 2, 1", // 1.98 -> 2, 0.99 -> 1
        "150, 3, 2" // 3.0 -> 3, 1.5 -> 2 (half up)
    })
    void rounds_half_up_to_the_cent(long totalCents, long expectedFee, long expectedDaily) {
        InterestCalculation calculation =
                InterestCalculation.forOverdueInvoice(Money.ofCents(totalCents, "BRL"), true, ACCRUAL_DATE);

        assertThat(calculation.lateFee().cents()).isEqualTo(expectedFee);
        assertThat(calculation.dailyInterest().cents()).isEqualTo(expectedDaily);
    }

    @Test
    @DisplayName("fee and daily interest stay separate all the way to billing-service")
    void keeps_the_two_charges_distinct() {
        // POST /invoices/{id}/interest takes {feeCents, dailyInterestCents} as two fields, so
        // summing them here would destroy information the invoice needs.
        InterestCalculation calculation =
                InterestCalculation.forOverdueInvoice(Money.ofCents(200_000L, "BRL"), true, ACCRUAL_DATE);

        assertThat(calculation.lateFee().cents()).isEqualTo(4_000L);
        assertThat(calculation.dailyInterest().cents()).isEqualTo(2_000L);
        assertThat(calculation.lateFee().currencyCode()).isEqualTo("BRL");
    }
}
