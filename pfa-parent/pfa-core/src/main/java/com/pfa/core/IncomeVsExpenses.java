package com.pfa.core;

import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;

/**
 * Income versus expenses comparison per calendar month.
 * Each entry maps a YearMonth to a pair of Money values: total income and total expenses.
 */
public record IncomeVsExpenses(Map<YearMonth, MonthEntry> monthlyData) {

    public IncomeVsExpenses {
        Objects.requireNonNull(monthlyData, "monthlyData must not be null");
        monthlyData = Map.copyOf(monthlyData);
    }

    /**
     * A single month's income and expenses totals.
     */
    public record MonthEntry(Money income, Money expenses) {
        public MonthEntry {
            Objects.requireNonNull(income, "income must not be null");
            Objects.requireNonNull(expenses, "expenses must not be null");
        }
    }
}
