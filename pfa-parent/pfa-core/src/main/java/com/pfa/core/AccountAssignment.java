package com.pfa.core;

import java.util.Objects;

/**
 * Associates an import source with a specific account.
 */
public record AccountAssignment(String accountId, Bank bank, AccountKind kind) {

    public AccountAssignment {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(bank, "bank must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }
}
