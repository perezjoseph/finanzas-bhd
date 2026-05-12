package com.pfa.core;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A normalized financial transaction extracted from a bank statement.
 * Immutable record holding all canonical fields after normalization.
 */
public record Transaction(
        UUID id,
        String accountId,
        LocalDate date,
        String description,
        Money amount,
        Direction direction,
        Bank bank,
        Optional<String> transactionType,
        Optional<String> category,
        List<String> tags,
        boolean isInternalTransfer,
        Set<FieldIssue> issues,
        String sourceFileHash
) {

    public Transaction {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(bank, "bank must not be null");
        Objects.requireNonNull(transactionType, "transactionType must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(tags, "tags must not be null");
        Objects.requireNonNull(issues, "issues must not be null");
        Objects.requireNonNull(sourceFileHash, "sourceFileHash must not be null");

        // Defensive copies for mutable collections
        tags = List.copyOf(tags);
        issues = Set.copyOf(issues);
    }
}
