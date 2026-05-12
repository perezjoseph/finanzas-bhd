package com.pfa.core;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;

/**
 * Monthly savings rate: (income - expenses) / income expressed as a percentage per month.
 * A positive rate means the user saved money; negative means they spent more than they earned.
 * If income is zero for a month, the rate is null for that month (undefined).
 */
public record MonthlySavingsRate(Map<YearMonth, BigDecimal> ratesByMonth) {

    public MonthlySavingsRate {
        Objects.requireNonNull(ratesByMonth, "ratesByMonth must not be null");
    }
}
