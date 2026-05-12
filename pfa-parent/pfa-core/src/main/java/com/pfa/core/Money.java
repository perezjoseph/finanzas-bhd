package com.pfa.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An immutable monetary amount with a specific currency.
 * Amount is always stored with scale 2, rounded HALF_UP.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Adds another Money value. Both must share the same currency.
     */
    public Money plus(Money other) {
        if (this.currency != other.currency) {
            throw new IllegalArgumentException(
                    "Cannot add Money with different currencies: " + this.currency + " vs " + other.currency);
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Multiplies this amount by a factor.
     */
    public Money times(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor must not be null");
        return new Money(this.amount.multiply(factor), this.currency);
    }
}
