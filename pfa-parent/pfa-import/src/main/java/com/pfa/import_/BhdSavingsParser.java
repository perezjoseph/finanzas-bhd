package com.pfa.import_;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pfa.core.Currency;
import com.pfa.core.ExtractedText;
import com.pfa.core.FormatDescriptor;
import com.pfa.core.ParsedStatement;
import com.pfa.core.RawTransaction;
import com.pfa.core.StatementFooter;
import com.pfa.core.StatementHeader;
import com.pfa.core.StatementParser;

/**
 * Parser for BHD Savings Account statements (Estado de Cuenta - Cuentas de Ahorro).
 * Extracts header metadata, transaction rows, and validates footer totals.
 */
public class BhdSavingsParser implements StatementParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("Numero de Cuenta\\s*[:\\s]\\s*([\\dX]+-\\d+-\\d+)");
    private static final Pattern REGIONAL_NUMBER_PATTERN = Pattern.compile("Numero de Cuenta Regional\\s*[:\\s]\\s*(DO\\w+)");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("Moneda\\s*[:\\s]\\s*(US\\$|RD\\$)");
    private static final Pattern STATEMENT_DATE_PATTERN = Pattern.compile("Fecha de Corte\\s*[:\\s]\\s*(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern OPENING_BALANCE_PATTERN = Pattern.compile("Balance Inicial\\s*[:\\s]\\s*\\$?([\\d,]+\\.\\d{2})");
    private static final Pattern CLOSING_BALANCE_PATTERN = Pattern.compile("Balance Final\\s*[:\\s]\\s*\\$?([\\d,]+\\.\\d{2})");

    private static final Pattern DATE_START_PATTERN = Pattern.compile("^(\\d{2}/\\d{2}/\\d{4})\\s+(.*)");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\$?([\\d,]+\\.\\d{2})");

    private static final Pattern TOTAL_DEBITS_PATTERN = Pattern.compile(
            "Total.*?Debitos.*?\\$?([\\d,]+\\.\\d{2})", Pattern.CANON_EQ);
    private static final Pattern TOTAL_CREDITS_PATTERN = Pattern.compile(
            "Total.*?Creditos.*?\\$?([\\d,]+\\.\\d{2})", Pattern.CANON_EQ);

    @Override
    public ParsedStatement parse(ExtractedText text, FormatDescriptor format) {
        String fullText = String.join("\n", text.pages());

        StatementHeader header = parseHeader(fullText);
        List<RawTransaction> transactions = parseTransactions(fullText, header.currency());
        Optional<StatementFooter> footer = parseFooter(fullText);

        return new ParsedStatement(header, transactions, footer);
    }

    private StatementHeader parseHeader(String text) {
        String accountNumber = extractGroup(ACCOUNT_NUMBER_PATTERN, text).orElse("UNKNOWN");
        Optional<String> regionalNumber = extractGroup(REGIONAL_NUMBER_PATTERN, text);
        Currency currency = parseCurrency(extractGroup(CURRENCY_PATTERN, text).orElse("US$"));
        LocalDate statementDate = parseDate(extractGroup(STATEMENT_DATE_PATTERN, text).orElse("01/01/2000"));
        Optional<BigDecimal> openingBalance = extractGroup(OPENING_BALANCE_PATTERN, text).map(this::parseAmount);
        Optional<BigDecimal> closingBalance = extractGroup(CLOSING_BALANCE_PATTERN, text).map(this::parseAmount);

        return new StatementHeader(accountNumber, regionalNumber, currency, statementDate, openingBalance, closingBalance);
    }

    private List<RawTransaction> parseTransactions(String text, Currency currency) {
        List<RawTransaction> transactions = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        boolean inTransactionSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (isTableHeader(trimmed)) {
                inTransactionSection = true;
            } else if (isSkippableLine(trimmed)) {
                // skip balance initial lines
            } else if (inTransactionSection && isEndOfSection(trimmed)) {
                inTransactionSection = false;
            } else if (inTransactionSection) {
                parseTransactionLine(trimmed, currency).ifPresent(transactions::add);
            }
        }

        return transactions;
    }

    private boolean isTableHeader(String line) {
        return line.contains("Fecha") && line.contains("Ref") && line.contains("Detalle") && line.contains("Balance");
    }

    private boolean isSkippableLine(String line) {
        return line.contains("Balance Inicial") || line.contains("Balance al inicial");
    }

    private boolean isEndOfSection(String line) {
        return line.startsWith("Total") || line.matches("^\\d+\\s+CKS.*");
    }

    private Optional<RawTransaction> parseTransactionLine(String line, Currency currency) {
        Matcher dateMatcher = DATE_START_PATTERN.matcher(line);
        if (!dateMatcher.matches()) {
            return Optional.empty();
        }

        try {
            LocalDate date = parseDate(dateMatcher.group(1));
            String remainder = dateMatcher.group(2).trim();

            // Extract reference number (first numeric token)
            String reference = "";
            int spaceIdx = remainder.indexOf(' ');
            if (spaceIdx > 0) {
                String possibleRef = remainder.substring(0, spaceIdx);
                if (possibleRef.matches("\\d+")) {
                    reference = possibleRef;
                    remainder = remainder.substring(spaceIdx).trim();
                }
            }

            // Extract amounts from the end of the line
            List<BigDecimal> amounts = extractAmounts(remainder);
            if (amounts.isEmpty()) {
                return Optional.empty();
            }

            // Remove amounts from remainder to get description
            String description = removeAmountsFromEnd(remainder);

            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;

            if (amounts.size() >= 3) {
                debit = amounts.get(0);
                credit = amounts.get(1);
                // amounts.get(2) is balance, ignored
            } else if (amounts.size() == 2) {
                // One amount + balance: determine if debit or credit from context
                debit = amounts.get(0);
                // amounts.get(1) is balance
            }
            // Single amount is just balance, no transaction data

            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0 && amounts.size() == 1) {
                return Optional.empty();
            }

            return Optional.of(new RawTransaction(
                    date,
                    Optional.empty(),
                    description,
                    debit,
                    credit,
                    currency,
                    reference.isEmpty() ? Optional.empty() : Optional.of(reference),
                    Optional.empty()
            ));
        } catch (DateTimeParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private List<BigDecimal> extractAmounts(String text) {
        List<BigDecimal> amounts = new ArrayList<>();
        Matcher m = AMOUNT_PATTERN.matcher(text);
        while (m.find()) {
            amounts.add(parseAmount(m.group(1)));
        }
        return amounts;
    }

    private String removeAmountsFromEnd(String text) {
        // Find the first amount occurrence and take everything before it as description
        Matcher m = AMOUNT_PATTERN.matcher(text);
        if (m.find()) {
            return text.substring(0, m.start()).trim();
        }
        return text.trim();
    }

    private Optional<StatementFooter> parseFooter(String text) {
        Optional<String> totalDebitsStr = extractGroup(TOTAL_DEBITS_PATTERN, text);
        Optional<String> totalCreditsStr = extractGroup(TOTAL_CREDITS_PATTERN, text);
        Optional<String> closingBalanceStr = extractGroup(CLOSING_BALANCE_PATTERN, text);

        if (totalDebitsStr.isPresent() && totalCreditsStr.isPresent() && closingBalanceStr.isPresent()) {
            return Optional.of(new StatementFooter(
                    parseAmount(totalDebitsStr.get()),
                    parseAmount(totalCreditsStr.get()),
                    parseAmount(closingBalanceStr.get())
            ));
        }

        return Optional.empty();
    }

    private Currency parseCurrency(String symbol) {
        return switch (symbol) {
            case "RD$" -> Currency.DOP;
            case "US$" -> Currency.USD;
            default -> Currency.USD;
        };
    }

    private LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, DATE_FORMAT);
    }

    private BigDecimal parseAmount(String amountStr) {
        String cleaned = amountStr.replace("$", "").replace(",", "").trim();
        return new BigDecimal(cleaned);
    }

    private Optional<String> extractGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }
}
