package com.pfa.core;

import java.util.Map;
import java.util.Objects;

/**
 * Spending breakdown by account.
 */
public record AccountBreakdown(Map<String, Money> byAccount) {

    public AccountBreakdown {
        Objects.requireNonNull(byAccount, "byAccount must not be null");
        byAccount = Map.copyOf(byAccount);
    }
}
