package com.pfa.import_;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pfa.core.AccountAssignment;
import com.pfa.core.AccountKind;
import com.pfa.core.Bank;
import com.pfa.core.Currency;
import com.pfa.core.ExtractionMode;
import com.pfa.core.FieldIssue;
import com.pfa.core.FormatDescriptor;
import com.pfa.core.NormalizedTransaction;
import com.pfa.core.RawTransaction;
import com.pfa.core.SourceFormat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for DefaultTransactionNormalizer.
 * Validates critical invariants: currency preservation, amount non-negativity,
 * description length bound, and required field completeness.
 */
class TransactionNormalizerProperties {

    private final DefaultTransactionNormalizer normalizer = new DefaultTransactionNormalizer();

    private static final AccountAssignment DEFAULT_ACCOUNT =
            new AccountAssignment("test-account", Bank.BHD, AccountKind.SAVINGS);
    private static final FormatDescriptor DEFAULT_FORMAT =
            new FormatDescriptor(SourceFormat.BHD_SAVINGS, ExtractionMode.TEXT_LAYER, 0.95, java.util.Map.of());

    @Property
    void currencyIsNeverConverted(@ForAll("validRawTransactions") RawTransaction raw) {
        NormalizedTransaction result = normalizer.normalize(raw, DEFAULT_ACCOUNT, DEFAULT_FORMAT);
        assertEquals(raw.currency(), result.transaction().amount().currency(),
                "Currency must be preserved from raw to normalized");
    }

    @Property
    void amountIsNonNegative(@ForAll("validRawTransactions") RawTransaction raw) {
        NormalizedTransaction result = normalizer.normalize(raw, DEFAULT_ACCOUNT, DEFAULT_FORMAT);
        assertTrue(result.transaction().amount().amount().compareTo(BigDecimal.ZERO) >= 0,
                "Normalized amount must be non-negative");
    }

    @Property
    void descriptionLengthBound(@ForAll("rawTransactionsWithLongDescriptions") RawTransaction raw) {
        NormalizedTransaction result = normalizer.normalize(raw, DEFAULT_ACCOUNT, DEFAULT_FORMAT);
        assertTrue(result.transaction().description().length() <= 256,
                "Description must be at most 256 characters");
    }

    @Property
    void requiredFieldCompleteness(@ForAll("completeRawTransactions") RawTransaction raw) {
        NormalizedTransaction result = normalizer.normalize(raw, DEFAULT_ACCOUNT, DEFAULT_FORMAT);
        assertFalse(result.issues().contains(FieldIssue.MissingRequired),
                "Complete raw transactions should have no MissingRequired issues");
    }

    @SuppressWarnings("unused")
    @Provide
    Arbitrary<RawTransaction> validRawTransactions() {
        return Combinators.combine(
                Arbitraries.defaultFor(LocalDate.class),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),
                Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("99999.99")),
                Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("99999.99")),
                Arbitraries.of(Currency.values())
        ).as((date, desc, debit, credit, currency) -> new RawTransaction(
                date, Optional.empty(), desc, debit, credit, currency, Optional.empty(), Optional.empty()
        ));
    }

    @SuppressWarnings("unused")
    @Provide
    Arbitrary<RawTransaction> rawTransactionsWithLongDescriptions() {
        return Combinators.combine(
                Arbitraries.defaultFor(LocalDate.class),
                Arbitraries.strings().alpha().ofMinLength(200).ofMaxLength(500),
                Arbitraries.bigDecimals().between(BigDecimal.ONE, new BigDecimal("9999.99")),
                Arbitraries.of(Currency.values())
        ).as((date, desc, amount, currency) -> new RawTransaction(
                date, Optional.empty(), desc, amount, BigDecimal.ZERO, currency, Optional.empty(), Optional.empty()
        ));
    }

    @SuppressWarnings("unused")
    @Provide
    Arbitrary<RawTransaction> completeRawTransactions() {
        return Combinators.combine(
                Arbitraries.defaultFor(LocalDate.class),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(50),
                Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("9999.99")),
                Arbitraries.of(Currency.values())
        ).as((date, desc, amount, currency) -> new RawTransaction(
                date, Optional.empty(), desc, amount, BigDecimal.ZERO, currency, Optional.of("REF123"), Optional.empty()
        ));
    }
}
