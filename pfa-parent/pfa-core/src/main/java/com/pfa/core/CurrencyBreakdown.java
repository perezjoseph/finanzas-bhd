package com.pfa.core;

import java.util.Map;
import java.util.Objects;

/**
 * Spending breakdown by currency.
 */
public record CurrencyBreakdown(Map<Currency, Money> byCurrency) {

    public CurrencyBreakdown {
        Objects.requireNonNull(byCurrency, "byCurrency must not be null");
        byCurrency = Map.copyOf(byCurrency);
    }
}
