package com.pfa.import_;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Maps CSV column indices to transaction fields.
 * Each field stores the zero-based column index in the CSV, or empty if not mapped.
 */
public record ColumnMapping(
        String name,
        int dateColumn,
        int descriptionColumn,
        int amountColumn,
        OptionalInt debitColumn,
        OptionalInt creditColumn,
        OptionalInt currencyColumn,
        OptionalInt referenceColumn,
        Optional<String> dateFormat,
        Optional<String> defaultCurrency,
        char delimiter
) {

    public ColumnMapping {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(debitColumn, "debitColumn must not be null");
        Objects.requireNonNull(creditColumn, "creditColumn must not be null");
        Objects.requireNonNull(currencyColumn, "currencyColumn must not be null");
        Objects.requireNonNull(referenceColumn, "referenceColumn must not be null");
        Objects.requireNonNull(dateFormat, "dateFormat must not be null");
        Objects.requireNonNull(defaultCurrency, "defaultCurrency must not be null");

        if (dateColumn < 0) {
            throw new IllegalArgumentException("dateColumn must be >= 0");
        }
        if (descriptionColumn < 0) {
            throw new IllegalArgumentException("descriptionColumn must be >= 0");
        }
        if (amountColumn < 0 && debitColumn.isEmpty() && creditColumn.isEmpty()) {
            throw new IllegalArgumentException(
                    "Either amountColumn must be >= 0, or debitColumn/creditColumn must be specified");
        }
    }

    /**
     * Returns true if this mapping uses separate debit/credit columns instead of a single amount column.
     */
    public boolean usesSeparateDebitCredit() {
        return debitColumn.isPresent() && creditColumn.isPresent();
    }
}
