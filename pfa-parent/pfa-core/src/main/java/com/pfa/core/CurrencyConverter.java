package com.pfa.core;

import java.math.BigDecimal;

/**
 * Pure, deterministic currency converter backed by a rate store.
 * Enforces reciprocal consistency and supports triangulation.
 */
public interface CurrencyConverter {

    /**
     * Converts the source money to the target currency using stored rates.
     * Identity: convert(m, m.currency()) == m.
     *
     * @throws MissingRateException if the rate cannot be derived
     */
    Money convert(Money source, Currency target);

    /**
     * Returns the exchange rate from one currency to another.
     *
     * @throws MissingRateException if the rate cannot be derived
     */
    BigDecimal rate(Currency from, Currency to);

    /**
     * Sets the exchange rate for a currency pair. Automatically stores
     * the reciprocal rate (1/r) for the inverse pair.
     *
     * @throws IllegalArgumentException if rate is <= 0 or > 999,999
     */
    void setRate(Currency from, Currency to, BigDecimal rate);
}
