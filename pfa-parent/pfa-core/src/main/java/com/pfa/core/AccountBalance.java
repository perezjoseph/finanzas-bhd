package com.pfa.core;

import java.time.YearMonth;
import java.util.Objects;

/**
 * A balance snapshot for an account at a specific month.
 */
public record AccountBalance(String accountId, YearMonth month, Money balance) {

    public AccountBalance {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(month, "month must not be null");
        Objects.requireNonNull(balance, "balance must not be null");
    }
}
