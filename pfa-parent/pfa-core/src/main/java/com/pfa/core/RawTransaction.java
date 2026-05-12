package com.pfa.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A transaction as extracted directly from a bank statement, before normalization.
 */
public record RawTransaction(
        LocalDate transactionDate,
        Optional<LocalDate> postingDate,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        Currency currency,
        Optional<String> referenceNumber,
        Optional<String> cardLast4
) {

    public RawTransaction {
        Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        Objects.requireNonNull(postingDate, "postingDate must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(debit, "debit must not be null");
        Objects.requireNonNull(credit, "credit must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(referenceNumber, "referenceNumber must not be null");
        Objects.requireNonNull(cardLast4, "cardLast4 must not be null");
    }
}
