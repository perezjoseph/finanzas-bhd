package com.pfa.core;

import java.util.Objects;

/**
 * A bank account that transactions belong to.
 */
public record Account(
        String id,
        String displayName,
        Bank bank,
        AccountKind kind,
        Currency primaryCurrency
) {

    public Account {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(bank, "bank must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(primaryCurrency, "primaryCurrency must not be null");
    }
}
