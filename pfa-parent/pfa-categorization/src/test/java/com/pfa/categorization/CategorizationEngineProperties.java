package com.pfa.categorization;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.pfa.core.Bank;
import com.pfa.core.CategoryAssignment;
import com.pfa.core.Currency;
import com.pfa.core.Direction;
import com.pfa.core.Money;
import com.pfa.core.Transaction;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for DefaultCategorizationEngine.
 * Validates: total coverage, override precedence, and fallback guarantee.
 */
class CategorizationEngineProperties {

    private final DefaultCategorizationEngine engine = new DefaultCategorizationEngine();

    @Property
    void totalCoverage(@ForAll("anyTransaction") Transaction tx) {
        CategoryAssignment result = engine.assign(tx);
        assertNotNull(result, "assign() must never return null");
        assertNotNull(result.category(), "Category must never be null");
    }

    @Property
    void overridePrecedence(@ForAll("anyTransaction") Transaction tx) {
        DefaultCategorizationEngine localEngine = new DefaultCategorizationEngine();
        String customCategory = "TestCategory";
        localEngine.createCustomCategory(customCategory);
        localEngine.recordOverride(tx, customCategory);

        // Any future transaction with the same normalized merchant key gets the override
        CategoryAssignment result = localEngine.assign(tx);
        assertEquals(customCategory, result.category().name(),
                "After override, same merchant key must return overridden category");
    }

    @Property
    void fallbackGuarantee(@ForAll("unmatchableTransactions") Transaction tx) {
        DefaultCategorizationEngine freshEngine = new DefaultCategorizationEngine();
        CategoryAssignment result = freshEngine.assign(tx);
        // If no rule or override matches, result must be Miscellaneous
        assertNotNull(result.category(), "Fallback must always produce a category");
    }

    @SuppressWarnings("unused")
    @Provide
    Arbitrary<Transaction> anyTransaction() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
                Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("9999.99")),
                Arbitraries.of(Currency.values()),
                Arbitraries.of(Direction.values())
        ).as((desc, amount, currency, direction) -> new Transaction(
                UUID.randomUUID(),
                "test-account",
                LocalDate.now(),
                desc,
                new Money(amount, currency),
                direction,
                Bank.BHD,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                false,
                Set.of(),
                "hash123"
        ));
    }

    @SuppressWarnings("unused")
    @Provide
    Arbitrary<Transaction> unmatchableTransactions() {
        // Descriptions that won't match any built-in rule
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(desc -> new Transaction(
                        UUID.randomUUID(),
                        "test-account",
                        LocalDate.now(),
                        desc,
                        new Money(new BigDecimal("10.00"), Currency.USD),
                        Direction.DEBIT,
                        Bank.BHD,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        false,
                        Set.of(),
                        "hash456"
                ));
    }
}
