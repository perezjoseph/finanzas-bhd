package com.pfa.core;

import java.util.List;
import java.util.Objects;

/**
 * An immutable view of transactions paired with the filter criteria that produced them.
 * A null filter indicates the full unfiltered dataset.
 */
public record TransactionSet(
        List<Transaction> transactions,
        FilterCriteria filter
) {

    public TransactionSet {
        Objects.requireNonNull(transactions, "transactions must not be null");
        // filter may be null to indicate unfiltered
        transactions = List.copyOf(transactions);
    }
}
