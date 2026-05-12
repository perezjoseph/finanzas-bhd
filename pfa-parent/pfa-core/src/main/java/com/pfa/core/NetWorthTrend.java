package com.pfa.core;

import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;

/**
 * Net worth trend over time (total balance across all accounts per month).
 */
public record NetWorthTrend(Map<YearMonth, Money> monthlyNetWorth) {

    public NetWorthTrend {
        Objects.requireNonNull(monthlyNetWorth, "monthlyNetWorth must not be null");
        monthlyNetWorth = Map.copyOf(monthlyNetWorth);
    }
}
