package com.pfa.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import com.pfa.core.ExportEngine;
import com.pfa.core.ExportException;
import com.pfa.core.FilterCriteria;
import com.pfa.core.Transaction;
import com.pfa.core.TransactionSet;

/**
 * Default implementation of ExportEngine.
 * Exports transactions to CSV (UTF-8, CRLF) or Excel (SXSSF streaming).
 */
public class DefaultExportEngine implements ExportEngine {

    private static final String[] HEADERS = {
            "Date", "Account", "Description", "Amount", "Currency",
            "Direction", "Category", "Bank", "Tags", "Internal Transfer", "Source Hash"
    };

    @Override
    public void exportCsv(Path target, TransactionSet txs, FilterCriteria activeFilter) throws ExportException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            // Metadata comments
            writer.write("# Export Date: " + LocalDate.now());
            writer.write("\r\n");
            if (activeFilter != null) {
                writer.write("# Filter: " + formatFilter(activeFilter));
                writer.write("\r\n");
            }

            // Header row
            writer.write(String.join(",", HEADERS));
            writer.write("\r\n");

            // Data rows
            for (Transaction tx : txs.transactions()) {
                writer.write(toCsvRow(tx));
                writer.write("\r\n");
            }
        } catch (IOException e) {
            throw new ExportException("Failed to export CSV: " + e.getMessage(), e);
        }
    }

    @Override
    public void exportExcel(Path target, TransactionSet txs, FilterCriteria activeFilter) throws ExportException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            // Transactions sheet
            Sheet txSheet = workbook.createSheet("Transactions");
            writeExcelHeader(txSheet, workbook);
            writeExcelData(txSheet, txs.transactions());

            // Metadata sheet
            Sheet metaSheet = workbook.createSheet("Metadata");
            writeMetadata(metaSheet, activeFilter);

            // Write to file
            try (OutputStream os = Files.newOutputStream(target)) {
                workbook.write(os);
            }
        } catch (IOException e) {
            throw new ExportException("Failed to export Excel: " + e.getMessage(), e);
        }
    }

    private void writeExcelHeader(Sheet sheet, SXSSFWorkbook workbook) {
        Row headerRow = sheet.createRow(0);
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(boldFont);

        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeExcelData(Sheet sheet, List<Transaction> transactions) {
        int rowNum = 1;
        for (Transaction tx : transactions) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(tx.date().toString());
            row.createCell(1).setCellValue(tx.accountId());
            row.createCell(2).setCellValue(tx.description());
            row.createCell(3).setCellValue(tx.amount().amount().doubleValue());
            row.createCell(4).setCellValue(tx.amount().currency().name());
            row.createCell(5).setCellValue(tx.direction().name());
            row.createCell(6).setCellValue(tx.category().orElse(""));
            row.createCell(7).setCellValue(tx.bank().name());
            row.createCell(8).setCellValue(String.join(";", tx.tags()));
            row.createCell(9).setCellValue(tx.isInternalTransfer() ? "Yes" : "No");
            row.createCell(10).setCellValue(tx.sourceFileHash());
        }
    }

    private void writeMetadata(Sheet sheet, FilterCriteria filter) {
        int rowNum = 0;
        Row dateRow = sheet.createRow(rowNum++);
        dateRow.createCell(0).setCellValue("Export Date");
        dateRow.createCell(1).setCellValue(LocalDate.now().toString());

        if (filter != null) {
            Row filterRow = sheet.createRow(rowNum);
            filterRow.createCell(0).setCellValue("Filter");
            filterRow.createCell(1).setCellValue(formatFilter(filter));
        }
    }

    private String toCsvRow(Transaction tx) {
        return String.join(",",
                tx.date().toString(),
                escapeCsv(tx.accountId()),
                escapeCsv(tx.description()),
                tx.amount().amount().toPlainString(),
                tx.amount().currency().name(),
                tx.direction().name(),
                escapeCsv(tx.category().orElse("")),
                tx.bank().name(),
                escapeCsv(String.join(";", tx.tags())),
                tx.isInternalTransfer() ? "Yes" : "No",
                tx.sourceFileHash()
        );
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String formatFilter(FilterCriteria filter) {
        StringBuilder sb = new StringBuilder();
        filter.startDate().ifPresent(d -> sb.append("from=").append(d).append(" "));
        filter.endDate().ifPresent(d -> sb.append("to=").append(d).append(" "));
        if (!filter.accountIds().isEmpty()) {
            sb.append("accounts=").append(filter.accountIds()).append(" ");
        }
        if (!filter.currencies().isEmpty()) {
            sb.append("currencies=").append(filter.currencies()).append(" ");
        }
        if (!filter.categoryNames().isEmpty()) {
            sb.append("categories=").append(filter.categoryNames()).append(" ");
        }
        filter.merchantSubstring().ifPresent(m -> sb.append("merchant=").append(m).append(" "));
        filter.minAmount().ifPresent(a -> sb.append("min=").append(a).append(" "));
        filter.maxAmount().ifPresent(a -> sb.append("max=").append(a).append(" "));
        filter.keyword().ifPresent(k -> sb.append("keyword=").append(k).append(" "));
        return sb.toString().trim();
    }
}
