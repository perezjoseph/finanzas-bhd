package com.pfa.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * An exchange rate between two currencies at a specific point in time.
 * The rate must be greater than 0 and at most 999,999.
 */
public record ExchangeRate(
        Currency from,
        Currency to,
        BigDecimal rate,
        Instant updatedAt
) {

    private static final BigDecimal MAX_RATE = new BigDecimal("999999");

    public ExchangeRate {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be greater than 0, got " + rate);
        }
        if (rate.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException(
                    "Exchange rate must be at most 999,999, got " + rate);
        }
    }
}
