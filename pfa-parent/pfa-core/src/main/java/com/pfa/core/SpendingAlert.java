package com.pfa.core;

import java.util.Objects;

/**
 * An alert indicating unusual spending in a category compared to historical averages.
 */
public record SpendingAlert(String categoryName, Money currentAmount, Money historicalAverage) {

    public SpendingAlert {
        Objects.requireNonNull(categoryName, "categoryName must not be null");
        Objects.requireNonNull(currentAmount, "currentAmount must not be null");
        Objects.requireNonNull(historicalAverage, "historicalAverage must not be null");
    }
}
