package com.cardbilling.collections.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void rounds_a_percentage_half_up_to_the_cent() {
        assertThat(Money.ofCents(25L).percentage(new BigDecimal("0.02")).cents())
                .isEqualTo(1L); // 0.5 rounds up, not to even
        assertThat(Money.ofCents(24L).percentage(new BigDecimal("0.02")).cents())
                .isEqualTo(0L); // 0.48 rounds down
    }

    @Test
    @DisplayName("a percentage is exact, not a double approximation")
    void does_not_drift_on_amounts_a_double_cannot_hold() {
        // 0.02 has no exact binary representation, so the legacy's `total * 0.02` was only ever
        // approximately right. At this magnitude a double has already lost whole cents.
        Money huge = Money.ofCents(100_000_000_000_000_001L);

        assertThat(huge.percentage(new BigDecimal("0.01")).cents()).isEqualTo(1_000_000_000_000_000L);
    }

    @Test
    void adds_without_overflowing_silently() {
        assertThat(Money.ofCents(2_500L).plus(Money.ofCents(1_250L))).isEqualTo(Money.ofCents(3_750L));
        assertThat(Money.ZERO.plus(Money.ofCents(10L)).cents()).isEqualTo(10L);
    }

    @Test
    void knows_when_it_is_zero() {
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.ofCents(1L).isZero()).isFalse();
    }
}
