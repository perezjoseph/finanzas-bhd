package com.pfa.core;

/**
 * Granularity options for the spending trend chart.
 */
public enum TrendGranularity {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    private final String displayName;

    TrendGranularity(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
