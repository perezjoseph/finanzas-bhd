package com.pfa.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pfa.categorization.DefaultCategorizationEngine;
import com.pfa.core.AccountAssignment;
import com.pfa.core.AccountKind;
import com.pfa.core.Bank;
import com.pfa.core.CategorizationEngine;
import com.pfa.core.Currency;
import com.pfa.core.Direction;
import com.pfa.core.ExtractionMode;
import com.pfa.core.FormatDescriptor;
import com.pfa.core.NormalizedTransaction;
import com.pfa.core.RawTransaction;
import com.pfa.core.SourceFormat;
import com.pfa.core.Transaction;
import com.pfa.core.TransactionNormalizer;
import com.pfa.import_.CsvParseResult;
import com.pfa.import_.CsvParser;
import com.pfa.import_.DefaultTransactionNormalizer;

/**
 * End-to-end integration test for CSV import pipeline.
 * Verifies: CSV parsing → column auto-detection → transaction normalization → categorization.
 *
 * This test does NOT require launching the JavaFX UI.
 * Validates: Requirements 13.1, 3.1, 3.5, 5.1, 5.2
 */
class CsvImportEndToEndTest {

    private CsvParser csvParser;
    private TransactionNormalizer normalizer;
    private CategorizationEngine categorizationEngine;

    private static final AccountAssignment TEST_ACCOUNT =
            new AccountAssignment("savings-001", Bank.BHD, AccountKind.SAVINGS);
    private static final FormatDescriptor CSV_FORMAT =
            new FormatDescriptor(SourceFormat.CSV_GENERIC, ExtractionMode.CSV, 0.90, java.util.Map.of());

    @BeforeEach
    void setUp() {
        csvParser = new CsvParser();
        normalizer = new DefaultTransactionNormalizer();
        categorizationEngine = new DefaultCategorizationEngine();
    }

    @Test
    void importCsvWithAutoDetectedColumns() {
        // Standard bank CSV with recognizable headers.
        // Note: auto-detection finds Date, Description, Amount columns but not Currency,
        // so it defaults to USD for all transactions when no currency column is mapped.
        String csvContent = """
                Date,Description,Amount,Type
                2026-01-15,MASSY STORES Worthing-BB,-99.56,purchase
                2026-01-18,PAYPAL *NETFLIX,-12.99,subscription
                2026-01-22,Salary Deposit,3500.00,income
                2026-02-01,SHELL Gas Station,-45.00,purchase
                2026-02-05,ATM Withdrawal,-200.00,withdrawal
                """;

        // Step 1: Parse CSV
        CsvParseResult result = csvParser.parse(csvContent);
        assertInstanceOf(CsvParseResult.Success.class, result);

        CsvParseResult.Success success = (CsvParseResult.Success) result;
        List<RawTransaction> rawTransactions = success.transactions();
        assertEquals(5, rawTransactions.size(), "Should parse all 5 data rows");

        // Step 2: Normalize each transaction and apply categorization
        List<Transaction> transactions = rawTransactions.stream()
                .map(raw -> {
                    NormalizedTransaction normalized = normalizer.normalize(raw, TEST_ACCOUNT, CSV_FORMAT);
                    Transaction tx = normalized.transaction();
                    // Apply categorization
                    var assignment = categorizationEngine.assign(tx);
                    return new Transaction(
                            tx.id(), tx.accountId(), tx.date(), tx.description(),
                            tx.amount(), tx.direction(), tx.bank(), tx.transactionType(),
                            Optional.of(assignment.category().name()),
                            tx.tags(), tx.isInternalTransfer(), tx.issues(), tx.sourceFileHash()
                    );
                })
                .toList();

        assertEquals(5, transactions.size());

        // Step 3: Verify first transaction — MASSY STORES (Groceries)
        Transaction tx1 = transactions.get(0);
        assertEquals(LocalDate.of(2026, 1, 15), tx1.date());
        assertEquals("MASSY STORES Worthing-BB", tx1.description());
        assertEquals(new BigDecimal("99.56"), tx1.amount().amount());
        assertEquals(Currency.USD, tx1.amount().currency());
        assertEquals(Direction.DEBIT, tx1.direction());
        assertTrue(tx1.category().isPresent());
        assertEquals("Groceries", tx1.category().get(), "MASSY STORES should be categorized as Groceries");

        // Step 4: Verify PAYPAL transaction (Subscriptions)
        Transaction tx2 = transactions.get(1);
        assertEquals(LocalDate.of(2026, 1, 18), tx2.date());
        assertTrue(tx2.description().contains("PAYPAL"));
        assertEquals(new BigDecimal("12.99"), tx2.amount().amount());
        assertEquals(Currency.USD, tx2.amount().currency());
        assertEquals(Direction.DEBIT, tx2.direction());
        assertTrue(tx2.category().isPresent());
        assertEquals("Subscriptions", tx2.category().get(), "PAYPAL should be categorized as Subscriptions");

        // Step 5: Verify income transaction (credit)
        Transaction tx3 = transactions.get(2);
        assertEquals(LocalDate.of(2026, 1, 22), tx3.date());
        assertEquals("Salary Deposit", tx3.description());
        assertEquals(new BigDecimal("3500.00"), tx3.amount().amount());
        assertEquals(Currency.USD, tx3.amount().currency());
        assertEquals(Direction.CREDIT, tx3.direction());

        // Step 6: Verify SHELL (Fuel)
        Transaction tx4 = transactions.get(3);
        assertEquals(LocalDate.of(2026, 2, 1), tx4.date());
        assertTrue(tx4.description().contains("SHELL"));
        assertEquals(new BigDecimal("45.00"), tx4.amount().amount());
        assertEquals(Direction.DEBIT, tx4.direction());
        assertTrue(tx4.category().isPresent());
        assertEquals("Fuel", tx4.category().get(), "SHELL should be categorized as Fuel");

        // Step 7: Verify ATM (Cash Withdrawals)
        Transaction tx5 = transactions.get(4);
        assertEquals(LocalDate.of(2026, 2, 5), tx5.date());
        assertTrue(tx5.description().contains("ATM"));
        assertEquals(new BigDecimal("200.00"), tx5.amount().amount());
        assertEquals(Direction.DEBIT, tx5.direction());
        assertTrue(tx5.category().isPresent());
        assertEquals("Cash Withdrawals", tx5.category().get(), "ATM should be categorized as Cash Withdrawals");
    }

    @Test
    void importCsvWithSeparateDebitCreditColumns() {
        // CSV with separate Debit/Credit columns (common BHD export format)
        String csvContent = """
                Date,Description,Debit,Credit,Currency
                22/01/2026,MASSY STORES Oistins-BB,99.56,,USD
                24/01/2026,CRTRINTL: Wire Transfer,,3173.00,USD
                25/01/2026,Impuesto 0.15% Ley 288-04,4.76,,USD
                """;

        CsvParseResult result = csvParser.parse(csvContent);
        assertInstanceOf(CsvParseResult.Success.class, result);

        CsvParseResult.Success success = (CsvParseResult.Success) result;
        List<RawTransaction> rawTransactions = success.transactions();
        assertEquals(3, rawTransactions.size());

        // Normalize and categorize
        List<Transaction> transactions = rawTransactions.stream()
                .map(raw -> {
                    NormalizedTransaction normalized = normalizer.normalize(raw, TEST_ACCOUNT, CSV_FORMAT);
                    Transaction tx = normalized.transaction();
                    var assignment = categorizationEngine.assign(tx);
                    return new Transaction(
                            tx.id(), tx.accountId(), tx.date(), tx.description(),
                            tx.amount(), tx.direction(), tx.bank(), tx.transactionType(),
                            Optional.of(assignment.category().name()),
                            tx.tags(), tx.isInternalTransfer(), tx.issues(), tx.sourceFileHash()
                    );
                })
                .toList();

        // Verify debit transaction
        Transaction debitTx = transactions.get(0);
        assertEquals(new BigDecimal("99.56"), debitTx.amount().amount());
        assertEquals(Direction.DEBIT, debitTx.direction());
        assertEquals("Groceries", debitTx.category().get());

        // Verify credit transaction (wire transfer)
        Transaction creditTx = transactions.get(1);
        assertEquals(new BigDecimal("3173.00"), creditTx.amount().amount());
        assertEquals(Direction.CREDIT, creditTx.direction());
        assertEquals("Transfers", creditTx.category().get(), "CRTRINTL should be categorized as Transfers");

        // Verify tax transaction
        Transaction taxTx = transactions.get(2);
        assertEquals(new BigDecimal("4.76"), taxTx.amount().amount());
        assertEquals(Direction.DEBIT, taxTx.direction());
        assertEquals("Taxes", taxTx.category().get(), "Ley 288-04 should be categorized as Taxes");
    }

    @Test
    void importCsvWithDdMmYyyyDateFormat() {
        // CSV with dd/MM/yyyy date format (BHD standard)
        String csvContent = """
                Fecha,Detalle,Debitos,Creditos
                15/01/2026,Pago Intereses CA US$,,25.50
                20/01/2026,PAGO DE TC 4641 3300 0068 19,500.00,
                """;

        CsvParseResult result = csvParser.parse(csvContent);
        assertInstanceOf(CsvParseResult.Success.class, result);

        CsvParseResult.Success success = (CsvParseResult.Success) result;
        List<RawTransaction> rawTransactions = success.transactions();
        assertEquals(2, rawTransactions.size());

        // Verify date parsing with dd/MM/yyyy format
        assertEquals(LocalDate.of(2026, 1, 15), rawTransactions.get(0).transactionDate());
        assertEquals(LocalDate.of(2026, 1, 20), rawTransactions.get(1).transactionDate());

        // Normalize and categorize
        List<Transaction> transactions = rawTransactions.stream()
                .map(raw -> {
                    NormalizedTransaction normalized = normalizer.normalize(raw, TEST_ACCOUNT, CSV_FORMAT);
                    Transaction tx = normalized.transaction();
                    var assignment = categorizationEngine.assign(tx);
                    return new Transaction(
                            tx.id(), tx.accountId(), tx.date(), tx.description(),
                            tx.amount(), tx.direction(), tx.bank(), tx.transactionType(),
                            Optional.of(assignment.category().name()),
                            tx.tags(), tx.isInternalTransfer(), tx.issues(), tx.sourceFileHash()
                    );
                })
                .toList();

        // Interest payment should be categorized as Income
        assertEquals("Income", transactions.get(0).category().get());
        // TC payment should be categorized as Transfers
        assertEquals("Transfers", transactions.get(1).category().get());
    }

    @Test
    void importCsvUnrecognizedFormatReturnsMappingRequired() {
        // CSV with non-standard headers that can't be auto-detected
        String csvContent = """
                Ref,Posting,Narrative,Value,Ccy
                001,2026-01-15,Coffee Shop,-5.50,USD
                002,2026-01-16,Bus Fare,-2.00,DOP
                """;

        CsvParseResult result = csvParser.parse(csvContent);
        assertInstanceOf(CsvParseResult.MappingRequired.class, result);

        CsvParseResult.MappingRequired mappingRequired = (CsvParseResult.MappingRequired) result;
        assertEquals(5, mappingRequired.headers().size());
        assertFalse(mappingRequired.sampleRows().isEmpty());
        assertEquals(2, mappingRequired.sampleRows().size());
    }

    @Test
    void importCsvWithUserProvidedColumnMapping() {
        // CSV with non-standard headers, using user-provided mapping
        String csvContent = """
                Ref,Posting,Narrative,Value,Ccy
                001,2026-01-15,Coffee Shop,-5.50,USD
                002,2026-01-16,Bus Fare,-2.00,DOP
                003,2026-01-20,Salary,1500.00,BBD
                """;

        // User provides column mapping
        var mapping = new com.pfa.import_.ColumnMapping(
                "custom-bank",
                1,  // dateColumn = "Posting"
                2,  // descriptionColumn = "Narrative"
                3,  // amountColumn = "Value"
                java.util.OptionalInt.empty(),
                java.util.OptionalInt.empty(),
                java.util.OptionalInt.of(4),  // currencyColumn = "Ccy"
                java.util.OptionalInt.of(0),  // referenceColumn = "Ref"
                Optional.of("yyyy-MM-dd"),
                Optional.empty(),
                ','
        );

        CsvParseResult result = csvParser.parseWithMapping(csvContent, mapping);
        assertInstanceOf(CsvParseResult.Success.class, result);

        CsvParseResult.Success success = (CsvParseResult.Success) result;
        List<RawTransaction> rawTransactions = success.transactions();
        assertEquals(3, rawTransactions.size());

        // Normalize and categorize
        List<Transaction> transactions = rawTransactions.stream()
                .map(raw -> {
                    NormalizedTransaction normalized = normalizer.normalize(raw, TEST_ACCOUNT, CSV_FORMAT);
                    Transaction tx = normalized.transaction();
                    var assignment = categorizationEngine.assign(tx);
                    return new Transaction(
                            tx.id(), tx.accountId(), tx.date(), tx.description(),
                            tx.amount(), tx.direction(), tx.bank(), tx.transactionType(),
                            Optional.of(assignment.category().name()),
                            tx.tags(), tx.isInternalTransfer(), tx.issues(), tx.sourceFileHash()
                    );
                })
                .toList();

        // Verify dates parsed correctly
        assertEquals(LocalDate.of(2026, 1, 15), transactions.get(0).date());
        assertEquals(LocalDate.of(2026, 1, 16), transactions.get(1).date());
        assertEquals(LocalDate.of(2026, 1, 20), transactions.get(2).date());

        // Verify currencies preserved from CSV column
        assertEquals(Currency.USD, transactions.get(0).amount().currency());
        assertEquals(Currency.DOP, transactions.get(1).amount().currency());
        assertEquals(Currency.BBD, transactions.get(2).amount().currency());

        // Verify amounts (negative = debit, positive = credit)
        assertEquals(new BigDecimal("5.50"), transactions.get(0).amount().amount());
        assertEquals(Direction.DEBIT, transactions.get(0).direction());
        assertEquals(new BigDecimal("2.00"), transactions.get(1).amount().amount());
        assertEquals(Direction.DEBIT, transactions.get(1).direction());
        assertEquals(new BigDecimal("1500.00"), transactions.get(2).amount().amount());
        assertEquals(Direction.CREDIT, transactions.get(2).direction());

        // Verify all transactions get a category assigned (never null)
        for (Transaction tx : transactions) {
            assertTrue(tx.category().isPresent(), "Every transaction must have a category assigned");
            assertNotNull(tx.category().get());
            assertFalse(tx.category().get().isBlank());
        }
    }

    @Test
    void importEmptyCsvReturnsEmpty() {
        String csvContent = "";

        CsvParseResult result = csvParser.parse(csvContent);
        assertInstanceOf(CsvParseResult.Empty.class, result);
    }

    @Test
    void importCsvHeaderOnlyReturnsEmpty() {
        String csvContent = "Date,Description,Amount,Currency\n";

        CsvParseResult result = csvParser.parse(csvContent);
        assertInstanceOf(CsvParseResult.Empty.class, result);
    }

    @Test
    void currencyIsNeverConvertedDuringImport() {
        // Verify the hard invariant: original currency is preserved through the pipeline.
        // Use a user-provided column mapping that includes a currency column.
        String csvContent = """
                Date,Description,Amount,Currency
                2026-03-01,Purchase in DOP,-1500.00,DOP
                2026-03-02,Purchase in BBD,-75.00,BBD
                2026-03-03,Purchase in USD,-50.00,USD
                """;

        var mapping = new com.pfa.import_.ColumnMapping(
                "multi-currency",
                0,  // dateColumn = "Date"
                1,  // descriptionColumn = "Description"
                2,  // amountColumn = "Amount"
                java.util.OptionalInt.empty(),
                java.util.OptionalInt.empty(),
                java.util.OptionalInt.of(3),  // currencyColumn = "Currency"
                java.util.OptionalInt.empty(),
                Optional.of("yyyy-MM-dd"),
                Optional.empty(),
                ','
        );

        CsvParseResult result = csvParser.parseWithMapping(csvContent, mapping);
        assertInstanceOf(CsvParseResult.Success.class, result);

        CsvParseResult.Success success = (CsvParseResult.Success) result;
        List<Transaction> transactions = success.transactions().stream()
                .map(raw -> normalizer.normalize(raw, TEST_ACCOUNT, CSV_FORMAT).transaction())
                .toList();

        // Each transaction must retain its original currency — never converted
        assertEquals(Currency.DOP, transactions.get(0).amount().currency());
        assertEquals(new BigDecimal("1500.00"), transactions.get(0).amount().amount());

        assertEquals(Currency.BBD, transactions.get(1).amount().currency());
        assertEquals(new BigDecimal("75.00"), transactions.get(1).amount().amount());

        assertEquals(Currency.USD, transactions.get(2).amount().currency());
        assertEquals(new BigDecimal("50.00"), transactions.get(2).amount().amount());
    }
}
