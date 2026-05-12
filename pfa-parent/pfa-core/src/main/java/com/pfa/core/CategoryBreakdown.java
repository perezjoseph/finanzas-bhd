package com.pfa.core;

import java.util.Map;
import java.util.Objects;

/**
 * Spending breakdown by category, further grouped by currency.
 * When transactions span multiple currencies, each category maps to
 * a per-currency breakdown rather than mixing amounts from different currencies.
 */
public record CategoryBreakdown(Map<String, Map<Currency, Money>> byCategory) {

    public CategoryBreakdown {
        Objects.requireNonNull(byCategory, "byCategory must not be null");
        byCategory = Map.copyOf(byCategory);
    }
}
