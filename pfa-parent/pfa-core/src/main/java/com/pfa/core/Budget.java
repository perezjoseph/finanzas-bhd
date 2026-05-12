package com.pfa.core;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A spending budget for a specific category over a defined time period.
 * The limit amount must be between 0.01 and 999,999,999.99 (inclusive).
 */
public record Budget(
        UUID id,
        String categoryName,
        Money limit,
        BudgetPeriod period
) {

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    public Budget {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(categoryName, "categoryName must not be null");
        Objects.requireNonNull(limit, "limit must not be null");
        Objects.requireNonNull(period, "period must not be null");

        if (limit.amount().compareTo(MIN_AMOUNT) < 0 || limit.amount().compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(
                    "Budget limit must be between 0.01 and 999,999,999.99, got " + limit.amount());
        }
    }
}
