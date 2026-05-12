package com.pfa.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Criteria for filtering transactions. All fields are optional; when multiple
 * are present they combine with logical AND. A null or empty FilterCriteria
 * matches everything.
 */
public record FilterCriteria(
        Optional<LocalDate> startDate,
        Optional<LocalDate> endDate,
        Set<String> accountIds,
        Set<Currency> currencies,
        Set<String> categoryNames,
        Optional<String> merchantSubstring,
        Optional<BigDecimal> minAmount,
        Optional<BigDecimal> maxAmount,
        Optional<String> keyword
) {

    public FilterCriteria {
        java.util.Objects.requireNonNull(startDate, "startDate must not be null");
        java.util.Objects.requireNonNull(endDate, "endDate must not be null");
        java.util.Objects.requireNonNull(merchantSubstring, "merchantSubstring must not be null");
        java.util.Objects.requireNonNull(minAmount, "minAmount must not be null");
        java.util.Objects.requireNonNull(maxAmount, "maxAmount must not be null");
        java.util.Objects.requireNonNull(keyword, "keyword must not be null");

        accountIds = accountIds != null ? Set.copyOf(accountIds) : Set.of();
        currencies = currencies != null ? Set.copyOf(currencies) : Set.of();
        categoryNames = categoryNames != null ? Set.copyOf(categoryNames) : Set.of();

        // Validate: start <= end
        if (startDate.isPresent() && endDate.isPresent()
                && startDate.get().isAfter(endDate.get())) {
            throw new IllegalArgumentException(
                    "Start date must not be after end date");
        }

        // Validate: min <= max
        if (minAmount.isPresent() && maxAmount.isPresent()
                && minAmount.get().compareTo(maxAmount.get()) > 0) {
            throw new IllegalArgumentException(
                    "Minimum amount must not be greater than maximum amount");
        }
    }
}
