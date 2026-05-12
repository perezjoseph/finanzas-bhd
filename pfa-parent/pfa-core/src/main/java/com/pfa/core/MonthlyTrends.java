package com.pfa.core;

import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;

/**
 * Monthly spending trends grouped by category.
 */
public record MonthlyTrends(Map<YearMonth, Map<String, Money>> monthlyByCategory) {

    public MonthlyTrends {
        Objects.requireNonNull(monthlyByCategory, "monthlyByCategory must not be null");
        monthlyByCategory = Map.copyOf(monthlyByCategory);
    }
}
