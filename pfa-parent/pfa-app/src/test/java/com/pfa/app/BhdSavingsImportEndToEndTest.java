package com.pfa.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pfa.analytics.DefaultAnalyticsEngine;
import com.pfa.categorization.DefaultCategorizationEngine;
import com.pfa.core.AccountAssignment;
import com.pfa.core.AccountKind;
import com.pfa.core.Bank;
import com.pfa.core.CategoryBreakdown;
import com.pfa.core.Currency;
import com.pfa.core.DefaultCurrencyConverter;
import com.pfa.core.Direction;
import com.pfa.core.ImportBatchResult;
import com.pfa.core.ImportOptions;
import com.pfa.core.ImportSource;
import com.pfa.core.Money;
import com.pfa.core.Transaction;
import com.pfa.core.TransactionSet;
import com.pfa.import_.DefaultFormatDetector;
import com.pfa.import_.DefaultStatementImporter;
import com.pfa.import_.DefaultTransactionNormalizer;

/**
 * End-to-end integration test: imports a synthetic BHD savings PDF through the
 * full pipeline (FormatDetector → Parser → TransactionNormalizer → CategorizationEngine),
 * then verifies analytics can compute category breakdown from the results.
 *
 * Does NOT require JavaFX — tests the backend pipeline only.
 */
class BhdSavingsImportEndToEndTest {

    @TempDir
    Path tempDir;

    private DefaultStatementImporter importer;
    private DefaultAnalyticsEngine analyticsEngine;

    @BeforeEach
    void setUp() {
        var formatDetector = new DefaultFormatDetector();
        var normalizer = new DefaultTransactionNormalizer();
        var categorizationEngine = new DefaultCategorizationEngine();
        var currencyConverter = new DefaultCurrencyConverter();

        // OCR engine is not needed for text-based PDFs; pass a stub that won't be called
        importer = new DefaultStatementImporter(
                formatDetector,
                new StubOcrEngine(),
                normalizer,
                categorizationEngine
        );

        analyticsEngine = new DefaultAnalyticsEngine(currencyConverter);
    }

    @Test
    void importBhdSavingsPdfExtractsTransactionsWithCategories() throws IOException {
        // 1. Create a synthetic BHD savings statement PDF
        Path pdfPath = createSyntheticBhdSavingsPdf();

        // 2. Run through the full import pipeline
        AccountAssignment account = new AccountAssignment("savings-usd-001", Bank.BHD, AccountKind.SAVINGS);
        ImportSource source = new ImportSource.LocalFile(pdfPath, account);
        ImportBatchResult result = importer.importAll(List.of(source), ImportOptions.defaults());

        // 3. Verify transactions are extracted (non-empty list)
        assertFalse(result.successes().isEmpty(), "Should extract at least one transaction");
        assertTrue(result.failures().isEmpty(), "Should have no failures");

        List<Transaction> transactions = result.successes();

        // Verify each transaction has required fields
        for (Transaction tx : transactions) {
            assertNotNull(tx.id(), "Transaction should have an ID");
            assertNotNull(tx.date(), "Transaction should have a date");
            assertFalse(tx.description().isBlank(), "Transaction should have a description");
            assertNotNull(tx.amount(), "Transaction should have an amount");
            assertTrue(tx.amount().amount().signum() > 0, "Transaction amount should be positive");

            // Verify category is assigned
            assertTrue(tx.category().isPresent(), "Each transaction should have a category assigned");
            assertFalse(tx.category().get().isBlank(), "Category should not be blank");

            // Verify currency is preserved as USD (the statement currency)
            assertEquals(Currency.USD, tx.amount().currency(),
                    "Currency should be preserved as USD from the statement");

            // Verify account assignment
            assertEquals("savings-usd-001", tx.accountId());
            assertEquals(Bank.BHD, tx.bank());
        }
    }

    @Test
    void importedTransactionsHaveCorrectAmounts() throws IOException {
        Path pdfPath = createSyntheticBhdSavingsPdf();
        AccountAssignment account = new AccountAssignment("savings-usd-001", Bank.BHD, AccountKind.SAVINGS);
        ImportSource source = new ImportSource.LocalFile(pdfPath, account);
        ImportBatchResult result = importer.importAll(List.of(source), ImportOptions.defaults());

        List<Transaction> transactions = result.successes();
        assertFalse(transactions.isEmpty());

        // The synthetic PDF has known amounts — verify at least one debit and one credit
        boolean hasDebit = transactions.stream().anyMatch(tx -> tx.direction() == Direction.DEBIT);
        boolean hasCredit = transactions.stream().anyMatch(tx -> tx.direction() == Direction.CREDIT);
        assertTrue(hasDebit, "Should have at least one debit transaction");
        assertTrue(hasCredit, "Should have at least one credit transaction");
    }

    @Test
    void analyticsEngineComputesCategoryBreakdownFromImportedTransactions() throws IOException {
        Path pdfPath = createSyntheticBhdSavingsPdf();
        AccountAssignment account = new AccountAssignment("savings-usd-001", Bank.BHD, AccountKind.SAVINGS);
        ImportSource source = new ImportSource.LocalFile(pdfPath, account);
        ImportBatchResult result = importer.importAll(List.of(source), ImportOptions.defaults());

        List<Transaction> transactions = result.successes();
        assertFalse(transactions.isEmpty());

        // 4. Verify the analytics engine can compute category breakdown
        TransactionSet txSet = new TransactionSet(transactions, null);
        CategoryBreakdown breakdown = analyticsEngine.categoryBreakdown(txSet);

        assertNotNull(breakdown, "Category breakdown should not be null");
        assertFalse(breakdown.byCategory().isEmpty(), "Category breakdown should have entries");

        // Verify breakdown contains valid data
        for (Map.Entry<String, Map<Currency, Money>> entry : breakdown.byCategory().entrySet()) {
            assertFalse(entry.getKey().isBlank(), "Category name should not be blank");
            assertFalse(entry.getValue().isEmpty(), "Category should have at least one currency entry");

            for (Money money : entry.getValue().values()) {
                assertTrue(money.amount().signum() > 0, "Category total should be positive");
                assertEquals(Currency.USD, money.currency(), "Currency should be USD");
            }
        }
    }

    /**
     * Creates a synthetic BHD savings statement PDF with the expected format:
     * - Header with "Numero de Cuenta", "Moneda: US$", etc.
     * - Transaction table with Fecha/Ref./Detalle/Debitos/Creditos/Balance columns
     * - Summary row with totals
     */
    private Path createSyntheticBhdSavingsPdf() throws IOException {
        Path pdfPath = tempDir.resolve("bhd_savings_statement.pdf");

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(50, 750);

                // Header section
                writeLine(cs, "Estado de Cuenta");
                writeLine(cs, "Numero de Cuenta : XXXXXXX-002-2");
                writeLine(cs, "Numero de Cuenta Regional : DO89BCBH000000000XXXXXXX0022");
                writeLine(cs, "Moneda : US$");
                writeLine(cs, "Fecha de Corte : 28/02/2026");
                writeLine(cs, "Balance Inicial : $2,026.87");
                writeLine(cs, "Balance Final : $4,538.96");
                writeLine(cs, "");

                // Transaction table header
                writeLine(cs, "Fecha       Ref.      Detalle                              Debitos     Creditos    Balance");
                writeLine(cs, "");

                // Transaction rows (mix of debits and credits)
                writeLine(cs, "03/02/2026  1332001   CRTRINTL: HUMICLIMA (BARBADOS) LTD USD TRA  $0.00  $3,173.00  $5,199.87");
                writeLine(cs, "05/02/2026  1332002   Impuesto 0.15% Ley 288-04                   $4.76  $0.00      $5,195.11");
                writeLine(cs, "10/02/2026  1332003   PAGO DE TC XXXX XXXX XXXX 6819              $500.00  $0.00    $4,695.11");
                writeLine(cs, "15/02/2026  1332004   Ret.ley 253-12 10% DGII CapUS$              $6.22  $0.00      $4,688.89");
                writeLine(cs, "20/02/2026  1332005   Pago Intereses CA US$                        $0.00  $0.07      $4,688.96");
                writeLine(cs, "25/02/2026  1332006   MASSY STORES Worthing-BB                     $150.00  $0.00   $4,538.96");
                writeLine(cs, "");

                // Summary row
                writeLine(cs, "Total (Debitos) $660.98  Total (Creditos) $3,173.07");
                writeLine(cs, "Balance Final : $4,538.96");

                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            Files.write(pdfPath, baos.toByteArray());
        }

        return pdfPath;
    }

    private void writeLine(PDPageContentStream cs, String text) throws IOException {
        cs.showText(text);
        cs.newLineAtOffset(0, -14);
    }

    /**
     * Stub OCR engine that should never be called for text-based PDFs.
     */
    private static class StubOcrEngine implements com.pfa.core.OcrEngine {
        @Override
        public com.pfa.core.OcrResult extract(byte[] pdfBytes, com.pfa.core.OcrOptions options) {
            throw new UnsupportedOperationException("OCR should not be needed for text-based PDFs");
        }

        @Override
        public com.pfa.core.OcrMode activeMode() {
            return com.pfa.core.OcrMode.CPU;
        }
    }
}
