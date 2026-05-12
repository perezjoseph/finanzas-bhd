package com.pfa.persistence;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pfa.core.Account;
import com.pfa.core.AccountKind;
import com.pfa.core.Bank;
import com.pfa.core.Budget;
import com.pfa.core.BudgetPeriod;
import com.pfa.core.Category;
import com.pfa.core.CategoryRule;
import com.pfa.core.Currency;
import com.pfa.core.Direction;
import com.pfa.core.ExchangeRate;
import com.pfa.core.FieldIssue;
import com.pfa.core.Money;
import com.pfa.core.SessionSnapshot;
import com.pfa.core.Transaction;

/**
 * Tests for SnapshotSerializer: verifies full round-trip serialization of all
 * SessionSnapshot fields including transactions, accounts, categories, learned rules,
 * exchange rates, budgets, and settings.
 */
class SnapshotSerializerTest {

    private SnapshotSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new SnapshotSerializer();
    }

    @Test
    void roundTripEmptySnapshot() throws IOException {
        SessionSnapshot original = new SessionSnapshot(
                "1.0.0", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());

        byte[] data = serializer.serialize(original);
        SessionSnapshot loaded = serializer.deserialize(data);

        assertEquals(original.schemaVersion(), loaded.schemaVersion());
        assertEquals(original.accounts(), loaded.accounts());
        assertEquals(original.transactions(), loaded.transactions());
        assertEquals(original.categories(), loaded.categories());
        assertEquals(original.learnedRules(), loaded.learnedRules());
        assertEquals(original.rates(), loaded.rates());
        assertEquals(original.budgets(), loaded.budgets());
        assertEquals(original.settings(), loaded.settings());
    }

    @Test
    void roundTripWithAllFields() throws IOException {
        UUID txId = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-15T12:00:00Z");

        Account account = new Account("acc-1", "BHD Savings USD", Bank.BHD, AccountKind.SAVINGS, Currency.USD);

        Transaction tx = new Transaction(
                txId, "acc-1", LocalDate.of(2026, 2, 15),
                "MASSY STORES Worthing-BB", new Money(new BigDecimal("99.56"), Currency.USD),
                Direction.DEBIT, Bank.BHD,
                Optional.of("purchase"), Optional.of("Groceries"),
                List.of("food", "barbados"), false,
                Set.of(FieldIssue.UnsupportedCurrency), "abc123hash");

        Category cat1 = new Category("Groceries", false);
        Category cat2 = new Category("My Custom", true);

        CategoryRule rule = new CategoryRule("massy stores", "Groceries");

        ExchangeRate rate = new ExchangeRate(Currency.USD, Currency.DOP, new BigDecimal("58.50"), now);

        Budget budget = new Budget(budgetId, "Groceries",
                new Money(new BigDecimal("500.00"), Currency.USD),
                new BudgetPeriod.Custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));

        SessionSnapshot original = new SessionSnapshot(
                "1.0.0",
                List.of(account),
                List.of(tx),
                List.of(cat1, cat2),
                List.of(rule),
                List.of(rate),
                List.of(budget),
                Map.of("baseCurrency", "USD", "vaultEnabled", "false"));

        byte[] data = serializer.serialize(original);
        SessionSnapshot loaded = serializer.deserialize(data);

        // Verify schema version
        assertEquals("1.0.0", loaded.schemaVersion());

        // Verify accounts
        assertEquals(1, loaded.accounts().size());
        Account loadedAccount = loaded.accounts().get(0);
        assertEquals("acc-1", loadedAccount.id());
        assertEquals("BHD Savings USD", loadedAccount.displayName());
        assertEquals(Bank.BHD, loadedAccount.bank());
        assertEquals(AccountKind.SAVINGS, loadedAccount.kind());
        assertEquals(Currency.USD, loadedAccount.primaryCurrency());

        // Verify transactions
        assertEquals(1, loaded.transactions().size());
        Transaction loadedTx = loaded.transactions().get(0);
        assertEquals(txId, loadedTx.id());
        assertEquals("acc-1", loadedTx.accountId());
        assertEquals(LocalDate.of(2026, 2, 15), loadedTx.date());
        assertEquals("MASSY STORES Worthing-BB", loadedTx.description());
        assertEquals(new BigDecimal("99.56"), loadedTx.amount().amount());
        assertEquals(Currency.USD, loadedTx.amount().currency());
        assertEquals(Direction.DEBIT, loadedTx.direction());
        assertEquals(Bank.BHD, loadedTx.bank());
        assertEquals(Optional.of("purchase"), loadedTx.transactionType());
        assertEquals(Optional.of("Groceries"), loadedTx.category());
        assertEquals(List.of("food", "barbados"), loadedTx.tags());
        assertEquals(false, loadedTx.isInternalTransfer());
        assertTrue(loadedTx.issues().contains(FieldIssue.UnsupportedCurrency));
        assertEquals("abc123hash", loadedTx.sourceFileHash());

        // Verify categories
        assertEquals(2, loaded.categories().size());
        assertEquals("Groceries", loaded.categories().get(0).name());
        assertEquals(false, loaded.categories().get(0).isCustom());
        assertEquals("My Custom", loaded.categories().get(1).name());
        assertEquals(true, loaded.categories().get(1).isCustom());

        // Verify learned rules
        assertEquals(1, loaded.learnedRules().size());
        assertEquals("massy stores", loaded.learnedRules().get(0).merchantKey());
        assertEquals("Groceries", loaded.learnedRules().get(0).categoryName());

        // Verify exchange rates
        assertEquals(1, loaded.rates().size());
        ExchangeRate loadedRate = loaded.rates().get(0);
        assertEquals(Currency.USD, loadedRate.from());
        assertEquals(Currency.DOP, loadedRate.to());
        assertEquals(0, new BigDecimal("58.50").compareTo(loadedRate.rate()));
        assertEquals(now, loadedRate.updatedAt());

        // Verify budgets
        assertEquals(1, loaded.budgets().size());
        Budget loadedBudget = loaded.budgets().get(0);
        assertEquals(budgetId, loadedBudget.id());
        assertEquals("Groceries", loadedBudget.categoryName());
        assertEquals(new BigDecimal("500.00"), loadedBudget.limit().amount());
        assertEquals(Currency.USD, loadedBudget.limit().currency());
        assertTrue(loadedBudget.period() instanceof BudgetPeriod.Custom);
        BudgetPeriod.Custom customPeriod = (BudgetPeriod.Custom) loadedBudget.period();
        assertEquals(LocalDate.of(2026, 1, 1), customPeriod.start());
        assertEquals(LocalDate.of(2026, 3, 31), customPeriod.end());

        // Verify settings
        assertEquals(2, loaded.settings().size());
        assertEquals("USD", loaded.settings().get("baseCurrency"));
        assertEquals("false", loaded.settings().get("vaultEnabled"));
    }

    @Test
    void roundTripWithMonthlyBudget() throws IOException {
        Budget budget = new Budget(UUID.randomUUID(), "Dining",
                new Money(new BigDecimal("200.00"), Currency.DOP),
                new BudgetPeriod.Monthly());

        SessionSnapshot original = new SessionSnapshot(
                "1.0.0", List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(budget), Map.of());

        byte[] data = serializer.serialize(original);
        SessionSnapshot loaded = serializer.deserialize(data);

        assertEquals(1, loaded.budgets().size());
        assertTrue(loaded.budgets().get(0).period() instanceof BudgetPeriod.Monthly);
    }

    @Test
    void roundTripWithOptionalFieldsEmpty() throws IOException {
        Transaction tx = new Transaction(
                UUID.randomUUID(), "acc-1", LocalDate.of(2026, 1, 1),
                "Some transaction", new Money(new BigDecimal("10.00"), Currency.DOP),
                Direction.CREDIT, Bank.BHD,
                Optional.empty(), Optional.empty(),
                List.of(), true, Set.of(), "hash456");

        SessionSnapshot original = new SessionSnapshot(
                "1.0.0", List.of(), List.of(tx), List.of(), List.of(), List.of(), List.of(), Map.of());

        byte[] data = serializer.serialize(original);
        SessionSnapshot loaded = serializer.deserialize(data);

        Transaction loadedTx = loaded.transactions().get(0);
        assertEquals(Optional.empty(), loadedTx.transactionType());
        assertEquals(Optional.empty(), loadedTx.category());
        assertEquals(true, loadedTx.isInternalTransfer());
    }

    @Test
    void deserializeInvalidJsonThrowsIOException() {
        byte[] garbage = "not valid json {{{".getBytes();
        assertThrows(IOException.class, () -> serializer.deserialize(garbage));
    }

    @Test
    void deserializeMissingSchemaVersionThrowsIOException() {
        byte[] noVersion = "{}".getBytes();
        assertThrows(IOException.class, () -> serializer.deserialize(noVersion));
    }

    @Test
    void schemaVersionIsPreserved() throws IOException {
        SessionSnapshot original = new SessionSnapshot(
                "2.1.0", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());

        byte[] data = serializer.serialize(original);
        SessionSnapshot loaded = serializer.deserialize(data);

        assertEquals("2.1.0", loaded.schemaVersion());
    }

    @Test
    void serializedOutputContainsSchemaVersion() {
        SessionSnapshot snapshot = new SessionSnapshot(
                "1.0.0", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());

        byte[] data = serializer.serialize(snapshot);
        String json = new String(data);

        assertTrue(json.contains("\"schemaVersion\""));
        assertTrue(json.contains("1.0.0"));
    }
}
