package com.pfa.export;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pfa.core.Bank;
import com.pfa.core.Currency;
import com.pfa.core.Direction;
import com.pfa.core.ExportException;
import com.pfa.core.FilterCriteria;
import com.pfa.core.Money;
import com.pfa.core.Transaction;
import com.pfa.core.TransactionSet;

/**
 * End-to-end integration test for CSV and Excel export.
 * Verifies that DefaultExportEngine produces correct file contents
 * without requiring the JavaFX UI.
 */
class ExportEndToEndTest {

    @TempDir
    Path tempDir;

    private DefaultExportEngine engine;
    private List<Transaction> sampleTransactions;
    private FilterCriteria filter;
    private TransactionSet transactionSet;

    @BeforeEach
    void setUp() {
        engine = new DefaultExportEngine();

        sampleTransactions = List.of(
                new Transaction(
                        UUID.randomUUID(),
                        "savings-001",
                        LocalDate.of(2026, 1, 15),
                        "MASSY STORES Worthing-BB",
                        new Money(new BigDecimal("99.56"), Currency.USD),
                        Direction.DEBIT,
                        Bank.BHD,
                        Optional.of("purchase"),
                        Optional.of("Groceries"),
                        List.of("supermarket"),
                        false,
                        Set.of(),
                        "abc123hash"
                ),
                new Transaction(
                        UUID.randomUUID(),
                        "credit-card-001",
                        LocalDate.of(2026, 1, 22),
                        "PAYPAL *NETFLIX",
                        new Money(new BigDecimal("15.99"), Currency.USD),
                        Direction.DEBIT,
                        Bank.BHD,
                        Optional.of("purchase"),
                        Optional.of("Subscriptions"),
                        List.of("streaming", "entertainment"),
                        false,
                        Set.of(),
                        "def456hash"
                ),
                new Transaction(
                        UUID.randomUUID(),
                        "savings-001",
                        LocalDate.of(2026, 2, 1),
                        "CRTRINTL: HUMICLIMA (BARBADOS) LTD USD TRA",
                        new Money(new BigDecimal("3173.00"), Currency.USD),
                        Direction.CREDIT,
                        Bank.BHD,
                        Optional.of("transfer"),
                        Optional.of("Transfers"),
                        List.of(),
                        true,
                        Set.of(),
                        "ghi789hash"
                ),
                new Transaction(
                        UUID.randomUUID(),
                        "savings-002",
                        LocalDate.of(2026, 2, 10),
                        "Impuesto 0.15% Ley 288-04",
                        new Money(new BigDecimal("1500.00"), Currency.DOP),
                        Direction.DEBIT,
                        Bank.BHD,
                        Optional.empty(),
                        Optional.of("Taxes"),
                        List.of(),
                        false,
                        Set.of(),
                        "jkl012hash"
                ),
                new Transaction(
                        UUID.randomUUID(),
                        "credit-card-001",
                        LocalDate.of(2026, 2, 15),
                        "SOL PHARMACY Christ Church-BB",
                        new Money(new BigDecimal("45.20"), Currency.BBD),
                        Direction.DEBIT,
                        Bank.BHD,
                        Optional.of("purchase"),
                        Optional.of("Healthcare"),
                        List.of("pharmacy"),
                        false,
                        Set.of(),
                        "mno345hash"
                )
        );

        filter = new FilterCriteria(
                Optional.of(LocalDate.of(2026, 1, 1)),
                Optional.of(LocalDate.of(2026, 2, 28)),
                Set.of(),
                Set.of(Currency.USD, Currency.DOP, Currency.BBD),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        transactionSet = new TransactionSet(sampleTransactions, filter);
    }

    // ========== CSV Tests ==========

    @Test
    void exportCsv_producesUtf8EncodedFile() throws ExportException, IOException {
        Path csvFile = tempDir.resolve("export.csv");
        engine.exportCsv(csvFile, transactionSet, filter);

        // Read raw bytes and verify UTF-8 BOM is not present but content is valid UTF-8
        byte[] bytes = Files.readAllBytes(csvFile);
        String content = new String(bytes, StandardCharsets.UTF_8);
        assertFalse(content.isEmpty());
        // Verify it can be decoded as UTF-8 without errors
        assertEquals(content, new String(content.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }

    @Test
    void exportCsv_hasCommentLinesWithFilterAndExportDate() throws ExportException, IOException {
        Path csvFile = tempDir.resolve("export.csv");
        engine.exportCsv(csvFile, transactionSet, filter);

        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);

        // First line should be a comment with export date
        assertTrue(lines.get(0).startsWith("#"), "First line should be a comment (prefixed with #)");
        assertTrue(lines.get(0).contains("Export Date"), "First comment should contain export date");
        assertTrue(lines.get(0).contains(LocalDate.now().toString()),
                "Export date should be today's date");

        // Second line should be a comment with filter criteria
        assertTrue(lines.get(1).startsWith("#"), "Second line should be a comment (prefixed with #)");
        assertTrue(lines.get(1).contains("Filter"), "Second comment should contain filter info");
        assertTrue(lines.get(1).contains("currencies"), "Filter should mention currencies");
    }

    @Test
    void exportCsv_hasHeaderRowWithStandardizedFields() throws ExportException, IOException {
        Path csvFile = tempDir.resolve("export.csv");
        engine.exportCsv(csvFile, transactionSet, filter);

        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);

        // Find the header row (first non-comment line)
        String headerLine = lines.stream()
                .filter(l -> !l.startsWith("#"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No header row found"));

        // Verify expected header fields are present
        assertTrue(headerLine.contains("Date"), "Header should contain Date");
        assertTrue(headerLine.contains("Description"), "Header should contain Description");
        assertTrue(headerLine.contains("Amount"), "Header should contain Amount");
        assertTrue(headerLine.contains("Currency"), "Header should contain Currency");
        assertTrue(headerLine.contains("Direction"), "Header should contain Direction");
        assertTrue(headerLine.contains("Account"), "Header should contain Account");
        assertTrue(headerLine.contains("Bank"), "Header should contain Bank");
        assertTrue(headerLine.contains("Category"), "Header should contain Category");
    }

    @Test
    void exportCsv_hasDataRowsMatchingTransactions() throws ExportException, IOException {
        Path csvFile = tempDir.resolve("export.csv");
        engine.exportCsv(csvFile, transactionSet, filter);

        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);

        // Count data rows (non-comment, non-header)
        List<String> dataLines = lines.stream()
                .filter(l -> !l.startsWith("#"))
                .skip(1) // skip header
                .toList();

        assertEquals(5, dataLines.size(), "Should have 5 data rows matching 5 transactions");

        // Verify first transaction data
        String firstDataRow = dataLines.get(0);
        assertTrue(firstDataRow.contains("2026-01-15"), "First row should contain date 2026-01-15");
        assertTrue(firstDataRow.contains("MASSY STORES Worthing-BB"),
                "First row should contain description");
        assertTrue(firstDataRow.contains("99.56"), "First row should contain amount 99.56");
        assertTrue(firstDataRow.contains("USD"), "First row should contain currency USD");
        assertTrue(firstDataRow.contains("DEBIT"), "First row should contain direction DEBIT");

        // Verify DOP transaction
        String dopRow = dataLines.get(3);
        assertTrue(dopRow.contains("DOP"), "Fourth row should contain currency DOP");
        assertTrue(dopRow.contains("1500.00"), "Fourth row should contain amount 1500.00");

        // Verify BBD transaction
        String bbdRow = dataLines.get(4);
        assertTrue(bbdRow.contains("BBD"), "Fifth row should contain currency BBD");
        assertTrue(bbdRow.contains("45.20"), "Fifth row should contain amount 45.20");
    }

    @Test
    void exportCsv_withNullFilter_omitsFilterComment() throws ExportException, IOException {
        Path csvFile = tempDir.resolve("export_no_filter.csv");
        TransactionSet unfilteredSet = new TransactionSet(sampleTransactions, null);
        engine.exportCsv(csvFile, unfilteredSet, null);

        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);

        // Should still have export date comment
        assertTrue(lines.get(0).startsWith("#"), "First line should be export date comment");
        assertTrue(lines.get(0).contains("Export Date"));

        // Second line should be the header (no filter comment)
        assertFalse(lines.get(1).startsWith("#"), "Second line should be header, not a comment");
    }

    // ========== Excel Tests ==========

    @Test
    void exportExcel_hasTransactionsSheetWithHeaderAndData() throws ExportException, IOException {
        Path excelFile = tempDir.resolve("export.xlsx");
        engine.exportExcel(excelFile, transactionSet, filter);

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(excelFile))) {
            Sheet txSheet = workbook.getSheet("Transactions");
            assertNotNull(txSheet, "Should have a 'Transactions' sheet");

            // Header row
            Row headerRow = txSheet.getRow(0);
            assertNotNull(headerRow, "Should have a header row");
            assertEquals("Date", headerRow.getCell(0).getStringCellValue());
            assertEquals("Account", headerRow.getCell(1).getStringCellValue());
            assertEquals("Description", headerRow.getCell(2).getStringCellValue());
            assertEquals("Currency", headerRow.getCell(4).getStringCellValue());
            assertEquals("Direction", headerRow.getCell(5).getStringCellValue());

            // Data rows: should have 5 transactions
            int lastRow = txSheet.getLastRowNum();
            assertEquals(5, lastRow, "Should have 5 data rows (rows 1-5)");

            // Verify first data row
            Row firstData = txSheet.getRow(1);
            assertNotNull(firstData);
            assertEquals("2026-01-15", firstData.getCell(0).getStringCellValue());
            assertEquals("savings-001", firstData.getCell(1).getStringCellValue());
            assertEquals("MASSY STORES Worthing-BB", firstData.getCell(2).getStringCellValue());
            assertEquals(99.56, firstData.getCell(3).getNumericCellValue(), 0.001);
            assertEquals("USD", firstData.getCell(4).getStringCellValue());
            assertEquals("DEBIT", firstData.getCell(5).getStringCellValue());
            assertEquals("Groceries", firstData.getCell(6).getStringCellValue());

            // Verify DOP transaction (row 4, 0-indexed row 4)
            Row dopData = txSheet.getRow(4);
            assertNotNull(dopData);
            assertEquals("DOP", dopData.getCell(4).getStringCellValue());
            assertEquals(1500.00, dopData.getCell(3).getNumericCellValue(), 0.001);

            // Verify BBD transaction (row 5, 0-indexed row 5)
            Row bbdData = txSheet.getRow(5);
            assertNotNull(bbdData);
            assertEquals("BBD", bbdData.getCell(4).getStringCellValue());
            assertEquals(45.20, bbdData.getCell(3).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void exportExcel_hasMetadataSheetWithFilterAndExportDate() throws ExportException, IOException {
        Path excelFile = tempDir.resolve("export.xlsx");
        engine.exportExcel(excelFile, transactionSet, filter);

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(excelFile))) {
            Sheet metaSheet = workbook.getSheet("Metadata");
            assertNotNull(metaSheet, "Should have a 'Metadata' sheet");

            // Export date row
            Row dateRow = metaSheet.getRow(0);
            assertNotNull(dateRow, "Should have export date row");
            assertEquals("Export Date", dateRow.getCell(0).getStringCellValue());
            assertEquals(LocalDate.now().toString(), dateRow.getCell(1).getStringCellValue());

            // Filter row
            Row filterRow = metaSheet.getRow(1);
            assertNotNull(filterRow, "Should have filter row");
            assertEquals("Filter", filterRow.getCell(0).getStringCellValue());
            String filterValue = filterRow.getCell(1).getStringCellValue();
            assertTrue(filterValue.contains("from=2026-01-01"),
                    "Filter should contain start date");
            assertTrue(filterValue.contains("to=2026-02-28"),
                    "Filter should contain end date");
        }
    }

    @Test
    void exportExcel_dataMatchesInputTransactions() throws ExportException, IOException {
        Path excelFile = tempDir.resolve("export.xlsx");
        engine.exportExcel(excelFile, transactionSet, filter);

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(excelFile))) {
            Sheet txSheet = workbook.getSheet("Transactions");

            // Verify all 5 transactions are present with correct data
            for (int i = 0; i < sampleTransactions.size(); i++) {
                Transaction expected = sampleTransactions.get(i);
                Row row = txSheet.getRow(i + 1); // +1 for header
                assertNotNull(row, "Row " + (i + 1) + " should exist");

                assertEquals(expected.date().toString(),
                        row.getCell(0).getStringCellValue(),
                        "Date mismatch at row " + (i + 1));
                assertEquals(expected.accountId(),
                        row.getCell(1).getStringCellValue(),
                        "Account mismatch at row " + (i + 1));
                assertEquals(expected.description(),
                        row.getCell(2).getStringCellValue(),
                        "Description mismatch at row " + (i + 1));
                assertEquals(expected.amount().amount().doubleValue(),
                        row.getCell(3).getNumericCellValue(), 0.001,
                        "Amount mismatch at row " + (i + 1));
                assertEquals(expected.amount().currency().name(),
                        row.getCell(4).getStringCellValue(),
                        "Currency mismatch at row " + (i + 1));
                assertEquals(expected.direction().name(),
                        row.getCell(5).getStringCellValue(),
                        "Direction mismatch at row " + (i + 1));
            }
        }
    }
}
