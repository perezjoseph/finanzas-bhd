package com.pfa.import_;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.pfa.core.AccountAssignment;
import com.pfa.core.Currency;
import com.pfa.core.Direction;
import com.pfa.core.FieldIssue;
import com.pfa.core.FormatDescriptor;
import com.pfa.core.Money;
import com.pfa.core.NormalizedTransaction;
import com.pfa.core.RawTransaction;
import com.pfa.core.Transaction;
import com.pfa.core.TransactionNormalizer;

/**
 * Default implementation of TransactionNormalizer.
 * Maps RawTransaction fields to canonical Transaction records per normalization rules.
 * Never converts currency (hard invariant).
 */
public class DefaultTransactionNormalizer implements TransactionNormalizer {

    private static final int MAX_DESCRIPTION_LENGTH = 256;

    @Override
    public NormalizedTransaction normalize(RawTransaction raw, AccountAssignment account, FormatDescriptor format) {
        List<FieldIssue> issues = new ArrayList<>();

        String description = normalizeDescription(raw.description());
        BigDecimal amount = deriveAmount(raw, issues);
        Direction direction = deriveDirection(raw);
        Currency currency = validateCurrency(raw.currency(), issues);
        Money money = new Money(amount, currency);

        Transaction tx = new Transaction(
                UUID.randomUUID(),
                account.accountId(),
                raw.transactionDate(),
                description,
                money,
                direction,
                account.bank(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                false,
                Set.copyOf(issues),
                ""
        );

        return new NormalizedTransaction(tx, issues);
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        // Trim, collapse whitespace, truncate to 256 chars
        String normalized = description.trim().replaceAll("\\s+", " ");
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            normalized = normalized.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        return normalized;
    }

    private BigDecimal deriveAmount(RawTransaction raw, List<FieldIssue> issues) {
        BigDecimal debit = raw.debit();
        BigDecimal credit = raw.credit();

        BigDecimal amount;
        if (debit.compareTo(BigDecimal.ZERO) > 0) {
            amount = debit;
        } else if (credit.compareTo(BigDecimal.ZERO) > 0) {
            amount = credit;
        } else {
            issues.add(FieldIssue.AmountParseFailed);
            amount = BigDecimal.ZERO;
        }

        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private Direction deriveDirection(RawTransaction raw) {
        if (raw.debit().compareTo(BigDecimal.ZERO) > 0) {
            return Direction.DEBIT;
        }
        return Direction.CREDIT;
    }

    private Currency validateCurrency(Currency currency, List<FieldIssue> issues) {
        if (currency == null) {
            issues.add(FieldIssue.UnsupportedCurrency);
            return Currency.USD;
        }
        return currency;
    }
}
