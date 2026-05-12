package com.pfa.core;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Defines the time period for a budget: either a recurring monthly period
 * or a custom date range spanning 1–365 days.
 */
public sealed interface BudgetPeriod {

    /**
     * A recurring monthly budget that resets each calendar month.
     */
    record Monthly() implements BudgetPeriod {}

    /**
     * A custom date range budget. The span between start and end (inclusive)
     * must be between 1 and 365 days.
     */
    record Custom(LocalDate start, LocalDate end) implements BudgetPeriod {
        public Custom {
            Objects.requireNonNull(start, "start must not be null");
            Objects.requireNonNull(end, "end must not be null");
            long days = ChronoUnit.DAYS.between(start, end) + 1; // inclusive
            if (days < 1 || days > 365) {
                throw new IllegalArgumentException(
                        "Custom period must span 1–365 days, got " + days);
            }
        }
    }
}
