package com.pfa.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pfa.categorization.DefaultCategorizationEngine;
import com.pfa.core.AccountAssignment;
import com.pfa.core.AccountKind;
import com.pfa.core.Bank;
import com.pfa.core.Currency;
import com.pfa.core.ImportBatchResult;
import com.pfa.core.ImportOptions;
import com.pfa.core.ImportSource;
import com.pfa.core.Transaction;
import com.pfa.import_.DefaultFormatDetector;
import com.pfa.import_.DefaultOcrEngine;
import com.pfa.import_.DefaultStatementImporter;
import com.pfa.import_.DefaultTransactionNormalizer;

/**
 * End-to-end integration test: imports a synthetic BHD credit card PDF
 * through the full pipeline and verifies dual-currency transactions are
 * parsed correctly.
 *
 * Pipeline: StatementImporter -> FormatDetector -> BhdCreditCardParser
 *           -> TransactionNormalizer -> CategorizationEngine
 *
 * Validates: Requirements 2.1, 3.1, 3.5, 4 (currency preservation)
 */
class BhdCreditCardImportE2ETest {

    @TempDir
    Path tempDir;

    private DefaultStatementImporter importer;

    @BeforeEach
    void setUp() {
        importer = new DefaultStatementImporter(
                new DefaultFormatDetector(),
                new DefaultOcrEngine(""),
                new DefaultTransactionNormalizer(),
                new DefaultCategorizationEngine()
        );
    }

    @Test
    void importBhdCreditCardPdf_extractsDualCurrencyTransactions() throws IOException {
        // Generate a synthetic BHD credit card statement PDF
        byte[] pdfBytes = createBhdCreditCardPdf();
        Path pdfFile = tempDir.resolve("bhd_credit_card.pdf");
        Files.write(pdfFile, pdfBytes);

        AccountAssignment account = new AccountAssignment(
                "visa-6819", Bank.BHD, AccountKind.CREDIT_CARD);
        ImportSource source = new ImportSource.LocalFile(pdfFile, account);

        // Run through the full import pipeline
        ImportBatchResult result = importer.importAll(List.of(source), ImportOptions.defaults());

        // Verify no failures
        assertTrue(result.failures().isEmpty(),
                "Expected no failures but got: " + result.failures());
        assertTrue(result.emptyFiles().isEmpty(),
                "Expected no empty files but got: " + result.emptyFiles());

        // Verify transactions were extracted
        List<Transaction> transactions = result.successes();
        assertFalse(transactions.isEmpty(), "Expected transactions to be extracted");

        // Separate USD and DOP transactions
        List<Transaction> usdTransactions = transactions.stream()
                .filter(tx -> tx.amount().currency() == Currency.USD)
                .toList();
        List<Transaction> dopTransactions = transactions.stream()
                .filter(tx -> tx.amount().currency() == Currency.DOP)
                .toList();

        // Verify both currency sections produced transactions
        assertFalse(usdTransactions.isEmpty(),
                "Expected USD transactions from DOLARES section");
        assertFalse(dopTransactions.isEmpty(),
                "Expected DOP transactions from PESOS section");

        // Verify USD transactions have correct amounts
        assertEquals(2, usdTransactions.size(), "Expected 2 USD transactions");
        assertTrue(usdTransactions.stream()
                        .anyMatch(tx -> tx.description().contains("MASSY STORES")
                                && tx.amount().amount().compareTo(new BigDecimal("99.56")) == 0),
                "Expected MASSY STORES USD transaction with amount 99.56");
        assertTrue(usdTransactions.stream()
                        .anyMatch(tx -> tx.description().contains("PAYPAL")
                                && tx.amount().amount().compareTo(new BigDecimal("12.99")) == 0),
                "Expected PAYPAL USD transaction with amount 12.99");

        // Verify DOP transactions have correct amounts
        assertEquals(2, dopTransactions.size(), "Expected 2 DOP transactions");
        assertTrue(dopTransactions.stream()
                        .anyMatch(tx -> tx.description().contains("SUPERMERCADO NACIONAL")
                                && tx.amount().amount().compareTo(new BigDecimal("2500.00")) == 0),
                "Expected SUPERMERCADO NACIONAL DOP transaction with amount 2500.00");
        assertTrue(dopTransactions.stream()
                        .anyMatch(tx -> tx.description().contains("FARMACIA CAROL")
                                && tx.amount().amount().compareTo(new BigDecimal("850.75")) == 0),
                "Expected FARMACIA CAROL DOP transaction with amount 850.75");

        // Verify all transactions have the correct account assignment
        for (Transaction tx : transactions) {
            assertEquals("visa-6819", tx.accountId());
            assertEquals(Bank.BHD, tx.bank());
        }

        // Verify categories were assigned (not empty)
        for (Transaction tx : transactions) {
            assertTrue(tx.category().isPresent(),
                    "Expected category to be assigned for: " + tx.description());
        }
    }

    /**
     * Creates a synthetic BHD credit card statement PDF with the expected format:
     * - Header with "Estado de Cuenta de Tarjeta de Crédito" and "VISA MI PAIS"
     * - USD section: "TRANSACCIONES EN DOLARES US$" with 2 transactions
     * - DOP section: "TRANSACCIONES EN PESOS RD$" with 2 transactions
     * - TOTAL rows for each section
     */
    private byte[] createBhdCreditCardPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                cs.beginText();
                cs.setFont(font, 10);
                cs.newLineAtOffset(50, 750);

                // Header
                writeLine(cs, "Estado de Cuenta de Tarjeta de Cr\u00e9dito");
                writeLine(cs, "VISA MI PAIS");
                writeLine(cs, "Numero de tarjeta: 464133******6819");
                writeLine(cs, "Fecha de corte: 26/02/2026");
                writeLine(cs, "Fecha limite de pago: 23/03/2026");
                writeLine(cs, "Limite de credito: RD$ 20,000.00 y US$ 1,000.00");
                writeLine(cs, "");

                // USD Section
                writeLine(cs, "TRANSACCIONES EN DOLARES US$");
                writeLine(cs, "Fecha trans.  Fecha aplicacion  Numero de tarjeta  Concepto  Debitos  Creditos");
                writeLine(cs, "22/01/2026 27/01/2026 6819 MASSY STORES Worthing-BB 99.56 0.00");
                writeLine(cs, "05/02/2026 07/02/2026 6819 PAYPAL *NETFLIX* 402-935-7733-GB 12.99 0.00");
                writeLine(cs, "TOTAL DE TRANSACCIONES EN DOLARES US$ 112.55 0.00");
                writeLine(cs, "");

                // DOP Section
                writeLine(cs, "TRANSACCIONES EN PESOS RD$");
                writeLine(cs, "Fecha trans.  Fecha aplicacion  Numero de tarjeta  Concepto  Debitos  Creditos");
                writeLine(cs, "10/02/2026 11/02/2026 6819 SUPERMERCADO NACIONAL Santo Domingo-DO 2,500.00 0.00");
                writeLine(cs, "15/02/2026 16/02/2026 6819 FARMACIA CAROL Higuey-DO 850.75 0.00");
                writeLine(cs, "TOTAL DE TRANSACCIONES EN PESOS RD$ 3,350.75 0.00");

                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private void writeLine(PDPageContentStream cs, String text) throws IOException {
        cs.showText(text);
        cs.newLineAtOffset(0, -14);
    }
}
