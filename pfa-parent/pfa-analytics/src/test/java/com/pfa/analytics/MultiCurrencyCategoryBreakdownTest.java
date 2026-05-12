package com.pfa.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pfa.core.Bank;
import com.pfa.core.CategoryBreakdown;
import com.pfa.core.Currency;
import com.pfa.core.DefaultCurrencyConverter;
import com.pfa.core.Direction;
import com.pfa.core.Money;
import com.pfa.core.Transaction;
import com.pfa.core.TransactionSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that categoryBreakdown correctly groups by category AND currency,
 * producing separate entries per currency when transactions span multiple currencies.
 */
class MultiCurrencyCategoryBreakdownTest {

    private DefaultAnalyticsEngine engine;

    @BeforeEach
    void setUp() {
        DefaultCurrencyConverter converter = new DefaultCurrencyConverter();
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("57"));
        converter.setRate(Currency.USD, Currency.BBD, new BigDecimal("2"));
        engine = new DefaultAnalyticsEngine(converter);
    }

    @Test
    void singleCurrency_groupsByCategory() {
        List<Transaction> txs = List.of(
                debitTx("Groceries", new Money(new BigDecimal("50.00"), Currency.USD)),
                debitTx("Groceries", new Money(new BigDecimal("30.00"), Currency.USD)),
                debitTx("Fuel", new Money(new BigDecimal("40.00"), Currency.USD))
        );

        CategoryBreakdown breakdown = engine.categoryBreakdown(new TransactionSet(txs, null));

        assertEquals(2, breakdown.byCategory().size());
        // Groceries should have one currency entry (USD)
        Map<Currency, Money> groceries = breakdown.byCategory().get("Groceries");
        assertNotNull(groceries);
        assertEquals(1, groceries.size());
        assertEquals(new BigDecimal("80.00"), groceries.get(Currency.USD).amount());

        // Fuel should have one currency entry (USD)
        Map<Currency, Money> fuel = breakdown.byCategory().get("Fuel");
        assertNotNull(fuel);
        assertEquals(1, fuel.size());
        assertEquals(new BigDecimal("40.00"), fuel.get(Currency.USD).amount());
    }

    @Test
    void multiCurrency_separateEntriesPerCurrency() {
        List<Transaction> txs = List.of(
                debitTx("Groceries", new Money(new BigDecimal("50.00"), Currency.USD)),
                debitTx("Groceries", new Money(new BigDecimal("2000.00"), Currency.DOP)),
                debitTx("Fuel", new Money(new BigDecimal("40.00"), Currency.USD)),
                debitTx("Fuel", new Money(new BigDecimal("100.00"), Currency.BBD))
        );

        CategoryBreakdown breakdown = engine.categoryBreakdown(new TransactionSet(txs, null));

        assertEquals(2, breakdown.byCategory().size());

        // Groceries should have two currency entries
        Map<Currency, Money> groceries = breakdown.byCategory().get("Groceries");
        assertNotNull(groceries);
        assertEquals(2, groceries.size());
        assertEquals(new BigDecimal("50.00"), groceries.get(Currency.USD).amount());
        assertEquals(new BigDecimal("2000.00"), groceries.get(Currency.DOP).amount());

        // Fuel should have two currency entries
        Map<Currency, Money> fuel = breakdown.byCategory().get("Fuel");
        assertNotNull(fuel);
        assertEquals(2, fuel.size());
        assertEquals(new BigDecimal("40.00"), fuel.get(Currency.USD).amount());
        assertEquals(new BigDecimal("100.00"), fuel.get(Currency.BBD).amount());
    }

    @Test
    void creditTransactions_excluded() {
        List<Transaction> txs = List.of(
                creditTx("Income", new Money(new BigDecimal("3000.00"), Currency.USD)),
                debitTx("Groceries", new Money(new BigDecimal("50.00"), Currency.USD))
        );

        CategoryBreakdown breakdown = engine.categoryBreakdown(new TransactionSet(txs, null));

        assertEquals(1, breakdown.byCategory().size());
        assertNull(breakdown.byCategory().get("Income"));
        assertNotNull(breakdown.byCategory().get("Groceries"));
    }

    @Test
    void emptyTransactions_emptyBreakdown() {
        CategoryBreakdown breakdown = engine.categoryBreakdown(new TransactionSet(List.of(), null));
        assertTrue(breakdown.byCategory().isEmpty());
    }

    private Transaction debitTx(String category, Money amount) {
        return new Transaction(
                UUID.randomUUID(),
                "account-1",
                LocalDate.of(2026, 1, 15),
                "Test transaction",
                amount,
                Direction.DEBIT,
                Bank.BHD,
                Optional.empty(),
                Optional.of(category),
                List.of(),
                false,
                Set.of(),
                "hash123"
        );
    }

    private Transaction creditTx(String category, Money amount) {
        return new Transaction(
                UUID.randomUUID(),
                "account-1",
                LocalDate.of(2026, 1, 15),
                "Test transaction",
                amount,
                Direction.CREDIT,
                Bank.BHD,
                Optional.empty(),
                Optional.of(category),
                List.of(),
                false,
                Set.of(),
                "hash123"
        );
    }
}
