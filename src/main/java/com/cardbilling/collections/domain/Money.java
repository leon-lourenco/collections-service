package com.cardbilling.collections.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in minor units (cents) with its currency. Interest and late fees are percentages of
 * an invoice total, and a percentage of a bare {@code long} is where rounding rules quietly go
 * wrong — this type is where "how do we round a fraction of a cent" is decided exactly once.
 *
 * <p>Rounding is HALF_UP on the cent, which is the rule the legacy's {@code Math.round(total *
 * rate)} applied for the positive amounts it dealt with, without the binary floating-point drift
 * that made it only accidentally correct.
 */
public record Money(long cents, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
    }

    public static Money ofCents(long cents, String currencyCode) {
        return new Money(cents, Currency.getInstance(currencyCode));
    }

    /** The given percentage of this amount, rounded HALF_UP to a whole cent. */
    public Money percentage(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate must not be null");
        long result = BigDecimal.valueOf(cents)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return new Money(result, currency);
    }

    public Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot add %s to %s".formatted(other.currency.getCurrencyCode(), currency.getCurrencyCode()));
        }
        return new Money(Math.addExact(cents, other.cents), currency);
    }

    public boolean isZero() {
        return cents == 0L;
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }
}
