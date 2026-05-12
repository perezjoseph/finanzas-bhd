package com.pfa.core;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Summary data from the footer of a bank statement, used for validation.
 */
public record StatementFooter(
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal closingBalance
) {

    public StatementFooter {
        Objects.requireNonNull(totalDebits, "totalDebits must not be null");
        Objects.requireNonNull(totalCredits, "totalCredits must not be null");
        Objects.requireNonNull(closingBalance, "closingBalance must not be null");
    }
}
