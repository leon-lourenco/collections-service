package com.cardbilling.collections.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount in minor units (cents).
 *
 * <p>Currency is deliberately not modelled, matching {@code billing-service}'s own {@code Money}:
 * this is a single-currency (BRL) issuer, so a currency field would be a constant threaded through
 * every operation and never read, and {@code billing-service} does not put one on the wire. What
 * this type is for is stopping cents from being passed around as a bare {@code long}, where an
 * amount owed, a late fee and a day count all look identical to the compiler.
 *
 * <p>Interest and late fees are percentages of an invoice total, and a percentage of a bare
 * {@code long} is where rounding rules quietly go wrong — so this is also the one place that
 * decides how a fraction of a cent is rounded. HALF_UP on the cent, which is the rule the legacy's
 * {@code Math.round(total * rate)} applied for the positive amounts it dealt with, without the
 * binary floating-point drift that made it only accidentally correct.
 */
public record Money(long cents) {

    public static final Money ZERO = new Money(0L);

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    /** The given percentage of this amount, rounded HALF_UP to a whole cent. */
    public Money percentage(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate must not be null");
        long result = BigDecimal.valueOf(cents)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return new Money(result);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public boolean isZero() {
        return cents == 0L;
    }

    @Override
    public String toString() {
        return cents + " cents";
    }
}
