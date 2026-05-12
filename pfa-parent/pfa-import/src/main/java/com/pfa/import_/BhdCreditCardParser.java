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
 * Parser for BHD Credit Card statements (Estado de Cuenta de Tarjeta de Crédito).
 * Handles dual-currency sections (PESOS and DOLARES) and multi-page continuations.
 */
public class BhdCreditCardParser implements StatementParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile(
            "Numero de tarjeta\\s*[:\\s]\\s*(\\d{6}\\*+\\d{4})");
    private static final Pattern STATEMENT_DATE_PATTERN = Pattern.compile(
            "Fecha de corte\\s*[:\\s]\\s*(\\d{2}/\\d{2}/\\d{4})");

    private static final String USD_SECTION_HEADER = "TRANSACCIONES EN DOLARES US$";
    private static final String DOP_SECTION_HEADER = "TRANSACCIONES EN PESOS RD$";
    private static final String TOTAL_PREFIX = "TOTAL DE TRANSACCIONES";

    private static final Pattern DATE_PAIR_PATTERN = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}/\\d{2}/\\d{4})\\s+(.*)");

    // Alternative pattern: description followed by dates embedded without space
    // e.g. "MASSY STORES Oistins-BB28/01/2026 30/01/2026  46.026819"
    private static final Pattern EMBEDDED_DATE_PATTERN = Pattern.compile(
            "^(.+?)(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}/\\d{2}/\\d{4})\\s+(.+)$");

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([\\d,]+\\.\\d{2})");

    @Override
    public ParsedStatement parse(ExtractedText text, FormatDescriptor format) {
        String fullText = String.join("\n", text.pages());

        StatementHeader header = parseHeader(fullText);
        List<RawTransaction> transactions = parseTransactions(fullText);
        Optional<StatementFooter> footer = parseFooter(fullText);

        return new ParsedStatement(header, transactions, footer);
    }

    private StatementHeader parseHeader(String fullText) {
        String cardNumber = extractGroup(CARD_NUMBER_PATTERN, fullText).orElse("UNKNOWN");
        LocalDate statementDate = extractGroup(STATEMENT_DATE_PATTERN, fullText)
                .map(this::parseDate)
                .orElse(LocalDate.of(2000, 1, 1));

        // Credit card statements are primarily USD for this user
        return new StatementHeader(
                cardNumber,
                Optional.empty(),
                Currency.USD,
                statementDate,
                Optional.empty(),
                Optional.empty()
        );
    }

    private List<RawTransaction> parseTransactions(String fullText) {
        List<RawTransaction> transactions = new ArrayList<>();
        String[] lines = fullText.split("\\r?\\n");

        Currency currentCurrency = null;
        boolean inTransactionSection = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.contains(USD_SECTION_HEADER)) {
                currentCurrency = Currency.USD;
                inTransactionSection = true;
            } else if (trimmed.contains(DOP_SECTION_HEADER)) {
                currentCurrency = Currency.DOP;
                inTransactionSection = true;
            } else if (trimmed.startsWith(TOTAL_PREFIX) || trimmed.contains(TOTAL_PREFIX)) {
                inTransactionSection = false;
            } else if (inTransactionSection && currentCurrency != null) {
                parseTransactionLine(trimmed, currentCurrency).ifPresent(transactions::add);
            }
        }

        return transactions;
    }

    private Optional<RawTransaction> parseTransactionLine(String line, Currency currency) {
        // Try standard format first: "DD/MM/YYYY DD/MM/YYYY CARD DESCRIPTION AMOUNTS"
        Matcher datePairMatcher = DATE_PAIR_PATTERN.matcher(line);
        if (datePairMatcher.matches()) {
            return parseStandardFormat(datePairMatcher, currency);
        }

        // Try embedded date format: "DESCRIPTION[DD/MM/YYYY DD/MM/YYYY  AMOUNT[CARD4]]"
        Matcher embeddedMatcher = EMBEDDED_DATE_PATTERN.matcher(line);
        if (embeddedMatcher.matches()) {
            return parseEmbeddedDateFormat(embeddedMatcher, currency);
        }

        return Optional.empty();
    }

    private Optional<RawTransaction> parseStandardFormat(Matcher datePairMatcher, Currency currency) {
        try {
            LocalDate transDate = parseDate(datePairMatcher.group(1));
            LocalDate postDate = parseDate(datePairMatcher.group(2));
            String remainder = datePairMatcher.group(3).trim();

            // Extract card last 4 digits (first 4-digit token after dates)
            String cardLast4 = "";
            int firstSpace = remainder.indexOf(' ');
            if (firstSpace > 0) {
                String token = remainder.substring(0, firstSpace);
                if (token.matches("\\d{4}")) {
                    cardLast4 = token;
                    remainder = remainder.substring(firstSpace).trim();
                }
            }

            // Extract amounts from end of line
            List<BigDecimal> amounts = extractAmountsFromEnd(remainder);
            String description = removeTrailingAmounts(remainder);

            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;

            if (amounts.size() >= 2) {
                debit = amounts.get(0);
                credit = amounts.get(1);
            } else if (amounts.size() == 1) {
                debit = amounts.get(0);
            }

            if (description.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new RawTransaction(
                    transDate,
                    Optional.of(postDate),
                    description,
                    debit,
                    credit,
                    currency,
                    Optional.empty(),
                    cardLast4.isEmpty() ? Optional.empty() : Optional.of(cardLast4)
            ));
        } catch (DateTimeParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<RawTransaction> parseEmbeddedDateFormat(Matcher embeddedMatcher, Currency currency) {
        try {
            String description = embeddedMatcher.group(1).trim();
            LocalDate transDate = parseDate(embeddedMatcher.group(2));
            LocalDate postDate = parseDate(embeddedMatcher.group(3));
            String amountPart = embeddedMatcher.group(4).trim();

            // The amount part may end with 4 digits for card last 4
            // e.g. "46.026819" means amount=46.02, card=6819
            // or "840.95" means amount=840.95, no card
            String cardLast4 = "";
            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;

            // Check if there are two amounts (debit and credit) separated by spaces
            // e.g. "1,393.03  1,490.95" in TOTAL lines (but those are filtered out)
            List<BigDecimal> amounts = extractAmountsFromEnd(amountPart);

            if (amounts.size() >= 2) {
                // Two amounts: first is debit, second is credit
                debit = amounts.get(0);
                credit = amounts.get(1);
            } else if (amounts.size() == 1) {
                // Single amount - may have card last 4 appended
                // Pattern: "46.026819" -> amount "46.02", card "6819"
                // Pattern: "840.95" -> amount "840.95", no card
                String amountStr = amountPart.trim();
                Pattern amountWithCard = Pattern.compile("^([\\d,]+\\.\\d{2})(\\d{4})$");
                Matcher acMatcher = amountWithCard.matcher(amountStr);
                if (acMatcher.matches()) {
                    debit = parseAmount(acMatcher.group(1));
                    cardLast4 = acMatcher.group(2);
                } else {
                    // Just an amount, no card suffix
                    debit = amounts.get(0);
                }
            }

            if (description.isEmpty()) {
                return Optional.empty();
            }

            // Determine if this is a credit (payment) based on description
            if (description.toUpperCase().contains("PAGO") && credit.compareTo(BigDecimal.ZERO) == 0) {
                credit = debit;
                debit = BigDecimal.ZERO;
            }

            return Optional.of(new RawTransaction(
                    transDate,
                    Optional.of(postDate),
                    description,
                    debit,
                    credit,
                    currency,
                    Optional.empty(),
                    cardLast4.isEmpty() ? Optional.empty() : Optional.of(cardLast4)
            ));
        } catch (DateTimeParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private List<BigDecimal> extractAmountsFromEnd(String text) {
        List<BigDecimal> amounts = new ArrayList<>();
        Matcher m = AMOUNT_PATTERN.matcher(text);
        while (m.find()) {
            String raw = m.group(1).replace(",", "");
            amounts.add(new BigDecimal(raw));
        }
        return amounts;
    }

    private String removeTrailingAmounts(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        int lastDescEnd = 0;
        int firstAmountStart = text.length();
        if (m.find()) {
            firstAmountStart = m.start();
        }
        return text.substring(lastDescEnd, firstAmountStart).trim();
    }

    private Optional<StatementFooter> parseFooter(String fullText) {
        // Extract total debits and credits from USD section totals
        Pattern totalPattern = Pattern.compile(
                "TOTAL DE TRANSACCIONES EN DOLARES.*?([\\d,]+\\.\\d{2}).*?([\\d,]+\\.\\d{2})");
        Matcher m = totalPattern.matcher(fullText);
        if (m.find()) {
            BigDecimal totalDebits = parseAmount(m.group(1));
            BigDecimal totalCredits = parseAmount(m.group(2));
            return Optional.of(new StatementFooter(totalDebits, totalCredits, totalDebits.subtract(totalCredits)));
        }
        return Optional.empty();
    }

    private LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, DATE_FORMAT);
    }

    private BigDecimal parseAmount(String amountStr) {
        return new BigDecimal(amountStr.replace(",", ""));
    }

    private Optional<String> extractGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }
}
