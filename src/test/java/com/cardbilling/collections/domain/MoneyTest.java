package com.cardbilling.collections.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void rounds_a_percentage_half_up_to_the_cent() {
        assertThat(Money.ofCents(25L, "BRL").percentage(new BigDecimal("0.02")).cents())
                .isEqualTo(1L); // 0.5 rounds up, not to even
        assertThat(Money.ofCents(24L, "BRL").percentage(new BigDecimal("0.02")).cents())
                .isEqualTo(0L); // 0.48 rounds down
    }

    @Test
    @DisplayName("a percentage is exact, not a double approximation")
    void does_not_drift_on_amounts_a_double_cannot_hold() {
        // 0.02 has no exact binary representation, so the legacy's `total * 0.02` was only ever
        // approximately right. At this magnitude a double has already lost whole cents.
        Money huge = Money.ofCents(100_000_000_000_000_001L, "BRL");

        assertThat(huge.percentage(new BigDecimal("0.01")).cents()).isEqualTo(1_000_000_000_000_000L);
    }

    @Test
    void keeps_the_currency_through_arithmetic() {
        Money fee = Money.ofCents(2_500L, "BRL");
        Money interest = Money.ofCents(1_250L, "BRL");

        assertThat(fee.plus(interest)).isEqualTo(Money.ofCents(3_750L, "BRL"));
        assertThat(fee.percentage(new BigDecimal("0.5")).currencyCode()).isEqualTo("BRL");
    }

    @Test
    @DisplayName("adding across currencies is a bug, not a conversion")
    void refuses_to_add_different_currencies() {
        assertThatThrownBy(() -> Money.ofCents(100L, "BRL").plus(Money.ofCents(100L, "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("BRL");
    }
}
