package com.pfa.core;

import java.util.List;
import java.util.Objects;

/**
 * The result of normalizing a raw transaction: the canonical Transaction
 * plus any field issues detected during normalization.
 */
public record NormalizedTransaction(Transaction transaction, List<FieldIssue> issues) {

    public NormalizedTransaction {
        Objects.requireNonNull(transaction, "transaction must not be null");
        Objects.requireNonNull(issues, "issues must not be null");
        issues = List.copyOf(issues);
    }
}
