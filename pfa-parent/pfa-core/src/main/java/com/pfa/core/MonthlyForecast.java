package com.pfa.core;

import java.util.Objects;

/**
 * End-of-month spending forecast based on current month data.
 */
public record MonthlyForecast(Money projectedTotal, int daysElapsed, int daysInMonth) {

    public MonthlyForecast {
        Objects.requireNonNull(projectedTotal, "projectedTotal must not be null");
        if (daysElapsed < 0) {
            throw new IllegalArgumentException("daysElapsed must be non-negative");
        }
        if (daysInMonth <= 0) {
            throw new IllegalArgumentException("daysInMonth must be positive");
        }
    }
}
