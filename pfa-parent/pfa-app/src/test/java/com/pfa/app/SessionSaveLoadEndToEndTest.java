package com.pfa.app;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pfa.core.Account;
import com.pfa.core.AccountKind;
import com.pfa.core.Bank;
import com.pfa.core.Budget;
import com.pfa.core.BudgetPeriod;
import com.pfa.core.Category;
import com.pfa.core.Currency;
import com.pfa.core.Direction;
import com.pfa.core.Money;
import com.pfa.core.SessionHandle;
import com.pfa.core.SessionSnapshot;
import com.pfa.core.Transaction;
import com.pfa.persistence.DefaultSessionManager;

/**
 * End-to-end test: save and load session, verify state restored.
 * <p>
 * This test exercises the full session lifecycle through DefaultSessionManager:
 * save a snapshot containing transactions, budgets, categories, and settings,
 * then load it back and verify all state is faithfully restored.
 * <p>
 * Validates: Requirements 10.1, 10.2, 10.4, 10.5
 */
class SessionSaveLoadEndToEndTest {

    @TempDir
    Path tempDir;

    private DefaultSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new DefaultSessionManager(tempDir);
    }

    @Test
    void saveAndLoadRestoresTransactions() {
        // Create realistic transactions spanning multiple currencies and accounts
        Transaction tx1 = new Transaction(
                UUID.randomUUID(), "savings-001",
                LocalDate.of(2026, 2, 15), "MASSY STORES Worthing-BB",
                new Money(new BigDecimal("99.56"), Currency.USD), Direction.DEBIT,
                Bank.BHD, Optional.of("purchase"), Optional.of("Groceries"),
                List.of(), false, Set.of(), "abc123hash"
        );
        Transaction tx2 = new Transaction(
                UUID.randomUUID(), "savings-001",
                LocalDate.of(2026, 2, 18), "CRTRINTL: HUMICLIMA (BARBADOS) LTD USD TRA",
                new Money(new BigDecimal("3173.00"), Currency.USD), Direction.CREDIT,
                Bank.BHD, Optional.of("transfer"), Optional.of("Transfers"),
                List.of("wire"), false, Set.of(), "def456hash"
        );
        Transaction tx3 = new Transaction(
                UUID.randomUUID(), "credit-card-001",
                LocalDate.of(2026, 2, 20), "Impuesto 0.15% Ley 288-04",
                new Money(new BigDecimal("4.76"), Currency.USD), Direction.DEBIT,
                Bank.BHD, Optional.empty(), Optional.of("Taxes"),
                List.of(), false, Set.of(), "ghi789hash"
        );
        Transaction tx4 = new Transaction(
                UUID.randomUUID(), "savings-dop",
                LocalDate.of(2026, 2, 22), "Pago Intereses CA US$",
                new Money(new BigDecimal("1500.00"), Currency.DOP), Direction.CREDIT,
                Bank.BHD, Optional.empty(), Optional.of("Income"),
                List.of(), false, Set.of(), "jkl012hash"
        );

        List<Transaction> transactions = List.of(tx1, tx2, tx3, tx4);

        // Create budgets
        Budget budget1 = new Budget(UUID.randomUUID(), "Groceries",
                new Money(new BigDecimal("500.00"), Currency.USD), new BudgetPeriod.Monthly());
        Budget budget2 = new Budget(UUID.randomUUID(), "Entertainment",
                new Money(new BigDecimal("200.00"), Currency.USD),
                new BudgetPeriod.Custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));

        List<Budget> budgets = List.of(budget1, budget2);

        // Create categories
        List<Category> categories = List.of(
                new Category("Groceries", false),
                new Category("Transfers", false),
                new Category("Taxes", false),
                new Category("Income", false),
                new Category("My Custom Category", true)
        );

        // Settings
        var settings = java.util.Map.of(
                "baseCurrency", "DOP",
                "ocrMode", "CPU"
        );

        // Build snapshot
        SessionSnapshot snapshot = new SessionSnapshot(
                "1.0.0",
                List.of(new Account("savings-001", "BHD Savings USD", Bank.BHD, AccountKind.SAVINGS, Currency.USD),
                        new Account("credit-card-001", "BHD VISA Mi Pais", Bank.BHD, AccountKind.CREDIT_CARD, Currency.USD),
                        new Account("savings-dop", "BHD Savings DOP", Bank.BHD, AccountKind.SAVINGS, Currency.DOP)),
                transactions,
                categories,
                List.of(),
                List.of(),
                budgets,
                settings
        );

        // Save the session
        SessionHandle handle = sessionManager.save("full-state-test", snapshot);
        assertNotNull(handle);
        assertTrue(Files.exists(handle.file()), "Session file should exist on disk");
        assertEquals("full-state-test", handle.name());

        // Load the session back
        SessionSnapshot loaded = sessionManager.load(handle);
        assertNotNull(loaded);

        // Verify schema version
        assertEquals("1.0.0", loaded.schemaVersion());

        // Verify transactions restored
        assertEquals(4, loaded.transactions().size(), "All 4 transactions should be restored");

        Transaction loadedTx1 = loaded.transactions().stream()
                .filter(t -> t.id().equals(tx1.id()))
                .findFirst().orElseThrow();
        assertEquals("MASSY STORES Worthing-BB", loadedTx1.description());
        assertEquals(new BigDecimal("99.56"), loadedTx1.amount().amount());
        assertEquals(Currency.USD, loadedTx1.amount().currency());
        assertEquals(Direction.DEBIT, loadedTx1.direction());
        assertEquals(LocalDate.of(2026, 2, 15), loadedTx1.date());
        assertEquals("savings-001", loadedTx1.accountId());
        assertEquals(Optional.of("Groceries"), loadedTx1.category());
        assertEquals(Bank.BHD, loadedTx1.bank());

        Transaction loadedTx2 = loaded.transactions().stream()
                .filter(t -> t.id().equals(tx2.id()))
                .findFirst().orElseThrow();
        assertEquals("CRTRINTL: HUMICLIMA (BARBADOS) LTD USD TRA", loadedTx2.description());
        assertEquals(new BigDecimal("3173.00"), loadedTx2.amount().amount());
        assertEquals(Direction.CREDIT, loadedTx2.direction());
        assertEquals(List.of("wire"), loadedTx2.tags());

        Transaction loadedTx4 = loaded.transactions().stream()
                .filter(t -> t.id().equals(tx4.id()))
                .findFirst().orElseThrow();
        assertEquals(Currency.DOP, loadedTx4.amount().currency(),
                "Multi-currency transactions should preserve original currency");
        assertEquals(new BigDecimal("1500.00"), loadedTx4.amount().amount());

        // Verify budgets restored
        assertEquals(2, loaded.budgets().size(), "Both budgets should be restored");

        Budget loadedBudget1 = loaded.budgets().stream()
                .filter(b -> b.id().equals(budget1.id()))
                .findFirst().orElseThrow();
        assertEquals("Groceries", loadedBudget1.categoryName());
        assertEquals(new BigDecimal("500.00"), loadedBudget1.limit().amount());
        assertEquals(Currency.USD, loadedBudget1.limit().currency());
        assertTrue(loadedBudget1.period() instanceof BudgetPeriod.Monthly);

        Budget loadedBudget2 = loaded.budgets().stream()
                .filter(b -> b.id().equals(budget2.id()))
                .findFirst().orElseThrow();
        assertEquals("Entertainment", loadedBudget2.categoryName());
        assertTrue(loadedBudget2.period() instanceof BudgetPeriod.Custom);
        BudgetPeriod.Custom customPeriod = (BudgetPeriod.Custom) loadedBudget2.period();
        assertEquals(LocalDate.of(2026, 1, 1), customPeriod.start());
        assertEquals(LocalDate.of(2026, 6, 30), customPeriod.end());

        // Verify categories restored
        assertEquals(5, loaded.categories().size(), "All categories should be restored");
        assertTrue(loaded.categories().stream().anyMatch(c -> c.name().equals("My Custom Category") && c.isCustom()),
                "Custom category should be preserved");

        // Verify settings restored
        assertEquals("DOP", loaded.settings().get("baseCurrency"));
        assertEquals("CPU", loaded.settings().get("ocrMode"));

        // Verify accounts restored
        assertEquals(3, loaded.accounts().size(), "All accounts should be restored");
    }

    @Test
    void saveAndLoadPreservesInternalTransferFlag() {
        Transaction internalTx = new Transaction(
                UUID.randomUUID(), "savings-001",
                LocalDate.of(2026, 3, 1), "PAGO DE TC 4641 3300 0068 19",
                new Money(new BigDecimal("500.00"), Currency.USD), Direction.DEBIT,
                Bank.BHD, Optional.empty(), Optional.of("Transfers"),
                List.of(), true, Set.of(), "transfer-hash"
        );

        SessionSnapshot snapshot = new SessionSnapshot(
                "1.0.0", List.of(), List.of(internalTx), List.of(),
                List.of(), List.of(), List.of(), java.util.Map.of()
        );

        SessionHandle handle = sessionManager.save("transfer-test", snapshot);
        SessionSnapshot loaded = sessionManager.load(handle);

        Transaction loadedTx = loaded.transactions().get(0);
        assertTrue(loadedTx.isInternalTransfer(),
                "Internal transfer flag must be preserved across save/load");
    }

    @Test
    void overwriteSessionReplacesContent() {
        // Save initial session with 1 transaction
        Transaction tx1 = new Transaction(
                UUID.randomUUID(), "savings-001",
                LocalDate.of(2026, 1, 10), "First Transaction",
                new Money(new BigDecimal("100.00"), Currency.USD), Direction.DEBIT,
                Bank.BHD, Optional.empty(), Optional.of("Miscellaneous"),
                List.of(), false, Set.of(), "hash1"
        );

        SessionSnapshot first = new SessionSnapshot(
                "1.0.0", List.of(), List.of(tx1), List.of(),
                List.of(), List.of(), List.of(), java.util.Map.of()
        );

        sessionManager.save("overwrite-test", first);

        // Save again with different content (simulates overwrite)
        Transaction tx2 = new Transaction(
                UUID.randomUUID(), "credit-card-001",
                LocalDate.of(2026, 2, 20), "Second Transaction",
                new Money(new BigDecimal("250.00"), Currency.DOP), Direction.CREDIT,
                Bank.BHD, Optional.empty(), Optional.of("Income"),
                List.of(), false, Set.of(), "hash2"
        );

        SessionSnapshot second = new SessionSnapshot(
                "1.0.0", List.of(), List.of(tx1, tx2), List.of(),
                List.of(), List.of(), List.of(), java.util.Map.of("baseCurrency", "USD")
        );

        SessionHandle handle2 = sessionManager.save("overwrite-test", second);

        // Load and verify the overwritten content
        SessionSnapshot loaded = sessionManager.load(handle2);
        assertEquals(2, loaded.transactions().size(),
                "Overwritten session should contain the updated transaction list");
        assertEquals("USD", loaded.settings().get("baseCurrency"));
    }

    @Test
    void listSessionsShowsSavedSessions() {
        SessionSnapshot snapshot = new SessionSnapshot(
                "1.0.0", List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), java.util.Map.of()
        );

        sessionManager.save("session-alpha", snapshot);
        sessionManager.save("session-beta", snapshot);

        List<SessionHandle> sessions = sessionManager.list();
        assertEquals(2, sessions.size(), "Both saved sessions should appear in list");

        List<String> names = sessions.stream().map(SessionHandle::name).toList();
        assertTrue(names.contains("session-alpha"));
        assertTrue(names.contains("session-beta"));
    }

    @Test
    void sessionNameMaxLength100Chars() {
        String longName = "A".repeat(100);
        SessionSnapshot snapshot = new SessionSnapshot(
                "1.0.0", List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), java.util.Map.of()
        );

        // Should succeed — exactly 100 chars
        SessionHandle handle = sessionManager.save(longName, snapshot);
        assertNotNull(handle);

        // Verify it can be loaded back
        SessionSnapshot loaded = sessionManager.load(handle);
        assertNotNull(loaded);
    }

    @Test
    void emptySessionSavesAndLoadsSuccessfully() {
        // Edge case: session with no transactions, no budgets, no settings
        SessionSnapshot emptySnapshot = new SessionSnapshot(
                "1.0.0", List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), java.util.Map.of()
        );

        SessionHandle handle = sessionManager.save("empty-session", emptySnapshot);
        SessionSnapshot loaded = sessionManager.load(handle);

        assertNotNull(loaded);
        assertEquals(0, loaded.transactions().size());
        assertEquals(0, loaded.budgets().size());
        assertEquals(0, loaded.accounts().size());
        assertTrue(loaded.settings().isEmpty());
    }
}
