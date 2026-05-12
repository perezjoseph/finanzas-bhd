package com.pfa.core;

import java.util.Objects;

/**
 * A detected recurring payment pattern.
 */
public record RecurringPayment(String merchantKey, int frequencyDays, Money averageAmount) {

    public RecurringPayment {
        Objects.requireNonNull(merchantKey, "merchantKey must not be null");
        Objects.requireNonNull(averageAmount, "averageAmount must not be null");
        if (frequencyDays <= 0) {
            throw new IllegalArgumentException("frequencyDays must be positive, got " + frequencyDays);
        }
    }
}
