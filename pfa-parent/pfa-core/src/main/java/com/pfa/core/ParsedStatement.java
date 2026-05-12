package com.pfa.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of parsing a bank statement: header metadata, extracted transactions,
 * and an optional footer with summary/validation data.
 */
public record ParsedStatement(
        StatementHeader header,
        List<RawTransaction> transactions,
        Optional<StatementFooter> footer
) {

    public ParsedStatement {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(transactions, "transactions must not be null");
        Objects.requireNonNull(footer, "footer must not be null");
        transactions = List.copyOf(transactions);
    }
}
