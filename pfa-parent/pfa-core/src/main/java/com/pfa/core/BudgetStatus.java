package com.pfa.core;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Status of a budget: how much has been spent vs the limit.
 */
public record BudgetStatus(Budget budget, Money spent, Money remaining, double percentUsed) {

    public BudgetStatus {
        Objects.requireNonNull(budget, "budget must not be null");
        Objects.requireNonNull(spent, "spent must not be null");
        Objects.requireNonNull(remaining, "remaining must not be null");
    }

    /**
     * Returns true if the budget's time period has ended.
     * Monthly budgets expire at the end of the current calendar month.
     * Custom budgets expire after their end date.
     */
    public boolean isExpired() {
        LocalDate today = LocalDate.now();
        return switch (budget.period()) {
            case BudgetPeriod.Monthly() -> false; // Monthly budgets recur, never expire
            case BudgetPeriod.Custom(LocalDate start, LocalDate end) -> today.isAfter(end);
        };
    }

    /**
     * Returns true if spending has reached or exceeded 80% of the budget limit.
     */
    public boolean isWarning() {
        return percentUsed >= 80.0 && percentUsed < 100.0;
    }

    /**
     * Returns true if spending has exceeded the budget limit.
     */
    public boolean isOverLimit() {
        return percentUsed >= 100.0;
    }
}
