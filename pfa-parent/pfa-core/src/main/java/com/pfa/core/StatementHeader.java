package com.pfa.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Metadata extracted from the header section of a bank statement.
 */
public record StatementHeader(
        String accountNumber,
        Optional<String> regionalNumber,
        Currency currency,
        LocalDate statementDate,
        Optional<BigDecimal> openingBalance,
        Optional<BigDecimal> closingBalance
) {

    public StatementHeader {
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(regionalNumber, "regionalNumber must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(statementDate, "statementDate must not be null");
        Objects.requireNonNull(openingBalance, "openingBalance must not be null");
        Objects.requireNonNull(closingBalance, "closingBalance must not be null");
    }
}
