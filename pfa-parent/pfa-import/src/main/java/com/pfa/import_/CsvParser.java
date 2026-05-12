package com.pfa.import_;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.pfa.core.Currency;
import com.pfa.core.RawTransaction;

/**
 * Parses CSV files into RawTransactions using either auto-detected layouts
 * or user-provided column mappings.
 */
public class CsvParser {

    private static final int MAX_SAMPLE_ROWS = 5;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    /**
     * Attempts to parse a CSV file. If the format cannot be auto-detected,
     * returns MappingRequired with headers and sample rows for user configuration.
     */
    public CsvParseResult parse(String content) {
        if (content == null || content.isBlank()) {
            return new CsvParseResult.Empty("File is empty or contains no data");
        }

        char delimiter = DelimiterDetector.detect(content);
        List<String[]> rows = splitRows(content, delimiter);

        if (rows.isEmpty()) {
            return new CsvParseResult.Empty("No data rows found");
        }

        // First row is likely a header
        String[] headers = rows.get(0);
        List<String[]> dataRows = rows.subList(1, rows.size());

        if (dataRows.isEmpty()) {
            return new CsvParseResult.Empty("Only header row found, no data");
        }

        // Try auto-detection of column layout
        Optional<ColumnMapping> autoMapping = autoDetectMapping(headers, delimiter);
        if (autoMapping.isPresent()) {
            return parseWithMapping(dataRows, autoMapping.get());
        }

        // Cannot auto-detect: return sample for user mapping
        List<String> headerList = List.of(headers);
        List<List<String>> sampleRows = dataRows.stream()
                .limit(MAX_SAMPLE_ROWS)
                .map(List::of)
                .toList();

        return new CsvParseResult.MappingRequired(headerList, sampleRows, delimiter);
    }

    /**
     * Parses CSV content using a user-provided column mapping.
     */
    public CsvParseResult parseWithMapping(String content, ColumnMapping mapping) {
        if (content == null || content.isBlank()) {
            return new CsvParseResult.Empty("File is empty");
        }

        List<String[]> rows = splitRows(content, mapping.delimiter());
        if (rows.size() <= 1) {
            return new CsvParseResult.Empty("No data rows found");
        }

        // Skip header row
        List<String[]> dataRows = rows.subList(1, rows.size());
        return parseWithMapping(dataRows, mapping);
    }

    private CsvParseResult parseWithMapping(List<String[]> dataRows, ColumnMapping mapping) {
        List<RawTransaction> transactions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (int i = 0; i < dataRows.size(); i++) {
            String[] row = dataRows.get(i);
            try {
                Optional<RawTransaction> tx = parseRow(row, mapping);
                tx.ifPresent(transactions::add);
            } catch (Exception e) {
                warnings.add("Row " + (i + 2) + ": " + e.getMessage());
            }
        }

        return new CsvParseResult.Success(transactions, warnings);
    }

    private Optional<RawTransaction> parseRow(String[] fields, ColumnMapping mapping) {
        if (fields.length <= mapping.dateColumn() || fields.length <= mapping.descriptionColumn()) {
            return Optional.empty();
        }

        String dateStr = fields[mapping.dateColumn()].trim();
        String description = fields[mapping.descriptionColumn()].trim();

        if (dateStr.isEmpty() || description.isEmpty()) {
            return Optional.empty();
        }

        LocalDate date = parseDate(dateStr, mapping.dateFormat().orElse(null));
        if (date == null) {
            return Optional.empty();
        }

        BigDecimal[] amounts = extractDebitCredit(fields, mapping);
        Currency currency = resolveCurrency(fields, mapping);
        Optional<String> reference = resolveReference(fields, mapping);

        return Optional.of(new RawTransaction(
                date, Optional.empty(), description, amounts[0], amounts[1], currency, reference, Optional.empty()
        ));
    }

    private BigDecimal[] extractDebitCredit(String[] fields, ColumnMapping mapping) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;

        if (mapping.usesSeparateDebitCredit()) {
            int debitCol = mapping.debitColumn().getAsInt();
            int creditCol = mapping.creditColumn().getAsInt();
            if (debitCol < fields.length) {
                debit = parseAmountSafe(fields[debitCol]);
            }
            if (creditCol < fields.length) {
                credit = parseAmountSafe(fields[creditCol]);
            }
        } else if (mapping.amountColumn() >= 0 && mapping.amountColumn() < fields.length) {
            BigDecimal amount = parseAmountSafe(fields[mapping.amountColumn()]);
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                debit = amount.negate();
            } else {
                credit = amount;
            }
        }

        return new BigDecimal[]{debit, credit};
    }

    private Currency resolveCurrency(String[] fields, ColumnMapping mapping) {
        Currency currency = mapping.defaultCurrency()
                .flatMap(this::parseCurrency)
                .orElse(Currency.USD);

        if (mapping.currencyColumn().isPresent()) {
            int currCol = mapping.currencyColumn().getAsInt();
            if (currCol < fields.length) {
                currency = parseCurrency(fields[currCol].trim()).orElse(currency);
            }
        }
        return currency;
    }

    private Optional<String> resolveReference(String[] fields, ColumnMapping mapping) {
        if (mapping.referenceColumn().isPresent()) {
            int refCol = mapping.referenceColumn().getAsInt();
            if (refCol < fields.length && !fields[refCol].trim().isEmpty()) {
                return Optional.of(fields[refCol].trim());
            }
        }
        return Optional.empty();
    }

    private Optional<ColumnMapping> autoDetectMapping(String[] headers, char delimiter) {
        int dateCol = findColumn(headers, this::isDateHeader);
        int descCol = findColumn(headers, this::isDescriptionHeader);
        int amountCol = findColumn(headers, this::isAmountHeader);
        int debitCol = findColumn(headers, this::isDebitHeader);
        int creditCol = findColumn(headers, this::isCreditHeader);

        if (dateCol < 0 || descCol < 0) {
            return Optional.empty();
        }

        if (amountCol < 0 && (debitCol < 0 || creditCol < 0)) {
            return Optional.empty();
        }

        return Optional.of(new ColumnMapping(
                "auto-detected",
                dateCol,
                descCol,
                amountCol,
                debitCol >= 0 ? java.util.OptionalInt.of(debitCol) : java.util.OptionalInt.empty(),
                creditCol >= 0 ? java.util.OptionalInt.of(creditCol) : java.util.OptionalInt.empty(),
                java.util.OptionalInt.empty(),
                java.util.OptionalInt.empty(),
                Optional.empty(),
                Optional.of("USD"),
                delimiter
        ));
    }

    private int findColumn(String[] headers, java.util.function.Predicate<String> matcher) {
        for (int i = 0; i < headers.length; i++) {
            if (matcher.test(headers[i].toLowerCase().trim())) {
                return i;
            }
        }
        return -1;
    }

    private boolean isDateHeader(String h) {
        return h.contains("date") || h.contains("fecha") || h.equals("dt");
    }

    private boolean isDescriptionHeader(String h) {
        return h.contains("description") || h.contains("desc") || h.contains("concepto")
                || h.contains("detalle") || h.contains("merchant") || h.contains("memo");
    }

    private boolean isAmountHeader(String h) {
        return h.equals("amount") || h.equals("monto") || h.equals("importe") || h.equals("value");
    }

    private boolean isDebitHeader(String h) {
        return h.contains("debit") || h.contains("debito") || h.contains("withdrawal") || h.contains("cargo");
    }

    private boolean isCreditHeader(String h) {
        return h.contains("credit") || h.contains("credito") || h.contains("deposit") || h.contains("abono");
    }

    private List<String[]> splitRows(String content, char delimiter) {
        List<String[]> rows = new ArrayList<>();
        for (String line : content.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            rows.add(splitLine(line, delimiter));
        }
        return rows;
    }

    private String[] splitLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());

        return fields.toArray(String[]::new);
    }

    private LocalDate parseDate(String dateStr, String formatHint) {
        if (formatHint != null && !formatHint.isEmpty()) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(formatHint));
            } catch (DateTimeParseException e) {
                // Fall through to auto-detection
            }
        }

        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        return null;
    }

    private BigDecimal parseAmountSafe(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        String cleaned = value.trim()
                .replace("$", "")
                .replace("RD$", "")
                .replace("US$", "")
                .replace(",", "")
                .replace("(", "-")
                .replace(")", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Optional<Currency> parseCurrency(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String upper = value.toUpperCase().trim();
        return switch (upper) {
            case "DOP", "RD$", "RD" -> Optional.of(Currency.DOP);
            case "USD", "US$", "US" -> Optional.of(Currency.USD);
            case "BBD", "BBD$" -> Optional.of(Currency.BBD);
            default -> Optional.empty();
        };
    }
}
