package com.pfa.import_;

import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import com.pfa.core.AccountAssignment;
import com.pfa.core.AccountKind;
import com.pfa.core.Bank;
import com.pfa.core.CategorizationEngine;
import com.pfa.core.CategoryAssignment;
import com.pfa.core.ErrorCode;
import com.pfa.core.ExtractedText;
import com.pfa.core.ExtractionMode;
import com.pfa.core.FormatDescriptor;
import com.pfa.core.FormatDetector;
import com.pfa.core.ImportBatchResult;
import com.pfa.core.ImportError;
import com.pfa.core.ImportOptions;
import com.pfa.core.ImportSource;
import com.pfa.core.ImportWarning;
import com.pfa.core.NormalizedTransaction;
import com.pfa.core.OcrEngine;
import com.pfa.core.OcrOptions;
import com.pfa.core.OcrPage;
import com.pfa.core.OcrResult;
import com.pfa.core.ParsedStatement;
import com.pfa.core.RawTransaction;
import com.pfa.core.SourceFormat;
import com.pfa.core.StatementFooter;
import com.pfa.core.StatementImporter;
import com.pfa.core.StatementParser;
import com.pfa.core.Transaction;
import com.pfa.core.TransactionNormalizer;

/**
 * Default implementation of StatementImporter.
 * Orchestrates format detection, parsing, normalization, and categorization.
 */
public class DefaultStatementImporter implements StatementImporter {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB

    private final FormatDetector formatDetector;
    private final OcrEngine ocrEngine;
    private final TransactionNormalizer normalizer;
    private final CategorizationEngine categorizationEngine;
    private final Set<String> importedHashes;

    public DefaultStatementImporter(
            FormatDetector formatDetector,
            OcrEngine ocrEngine,
            TransactionNormalizer normalizer,
            CategorizationEngine categorizationEngine) {
        this.formatDetector = formatDetector;
        this.ocrEngine = ocrEngine;
        this.normalizer = normalizer;
        this.categorizationEngine = categorizationEngine;
        this.importedHashes = new HashSet<>();
    }

    @Override
    public ImportBatchResult importAll(List<ImportSource> sources, ImportOptions options) {
        List<Transaction> successes = new ArrayList<>();
        List<ImportError> failures = new ArrayList<>();
        List<ImportWarning> warnings = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        List<String> emptyFiles = new ArrayList<>();

        for (ImportSource source : sources) {
            importSingle(source, options, successes, failures, warnings, duplicates, emptyFiles);
        }

        return new ImportBatchResult(successes, failures, warnings, duplicates, emptyFiles);
    }

    private void importSingle(
            ImportSource source,
            ImportOptions options,
            List<Transaction> successes,
            List<ImportError> failures,
            List<ImportWarning> warnings,
            List<String> duplicates,
            List<String> emptyFiles) {

        String filename = getFilename(source);
        byte[] bytes;
        try {
            bytes = readBytes(source);
        } catch (IOException e) {
            failures.add(new ImportError(filename, ErrorCode.CORRUPTED_FILE, "Cannot read file: " + e.getMessage()));
            return;
        }

        if (bytes.length == 0) {
            emptyFiles.add(filename);
            return;
        }

        if (bytes.length > MAX_FILE_SIZE) {
            failures.add(new ImportError(filename, ErrorCode.FILE_TOO_LARGE,
                    "File exceeds 10 MB limit: " + bytes.length + " bytes"));
            return;
        }

        String hash = computeSha256(bytes);
        if (!options.skipDuplicateCheck() && importedHashes.contains(hash)) {
            duplicates.add(filename);
            return;
        }

        FormatDescriptor format = formatDetector.detect(bytes, filename);
        if (format.format() == SourceFormat.UNKNOWN && format.mode() != ExtractionMode.OCR_REQUIRED) {
            failures.add(new ImportError(filename, ErrorCode.UNSUPPORTED_FORMAT,
                    "Unrecognized file format"));
            return;
        }

        List<RawTransaction> rawTransactions = extractTransactions(bytes, format, filename, options, failures, warnings);
        if (rawTransactions.isEmpty()) {
            emptyFiles.add(filename);
            return;
        }

        AccountAssignment account = getAccount(source);
        for (RawTransaction raw : rawTransactions) {
            NormalizedTransaction normalized = normalizer.normalize(raw, account, format);
            Transaction tx = applyCategory(normalized.transaction(), hash);
            successes.add(tx);
        }

        importedHashes.add(hash);
    }

    private List<RawTransaction> extractTransactions(
            byte[] bytes, FormatDescriptor format, String filename,
            ImportOptions options,
            List<ImportError> failures, List<ImportWarning> warnings) {

        ExtractedText text = extractText(bytes, format, options);
        if (text.pages().isEmpty()) {
            failures.add(new ImportError(filename, ErrorCode.UNREADABLE_PDF, "No text could be extracted"));
            return List.of();
        }

        StatementParser parser = selectParser(format);
        if (parser == null) {
            failures.add(new ImportError(filename, ErrorCode.UNSUPPORTED_FORMAT, "No parser for format: " + format.format()));
            return List.of();
        }

        ParsedStatement parsed = parser.parse(text, format);
        validateStatement(parsed, warnings);
        return parsed.transactions();
    }

    private ExtractedText extractText(byte[] bytes, FormatDescriptor format, ImportOptions options) {
        if (format.mode() == ExtractionMode.OCR_REQUIRED) {
            OcrResult ocrResult = ocrEngine.extract(bytes, OcrOptions.defaults());
            List<String> pages = ocrResult.pages().stream()
                    .map(OcrPage::text)
                    .toList();
            return new ExtractedText(pages);
        }

        // Use PDFBox text extraction with optional password
        try {
            String password = options.pdfPassword().orElse(null);
            var document = (password != null)
                    ? org.apache.pdfbox.Loader.loadPDF(bytes, password)
                    : org.apache.pdfbox.Loader.loadPDF(bytes);
            try (document) {
                var stripper = new org.apache.pdfbox.text.PDFTextStripper();
                List<String> pages = new ArrayList<>();
                int pageCount = document.getNumberOfPages();
                for (int i = 1; i <= pageCount; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    pages.add(stripper.getText(document));
                }
                return new ExtractedText(pages);
            }
        } catch (IOException e) {
            return new ExtractedText(List.of());
        }
    }

    private StatementParser selectParser(FormatDescriptor format) {
        return switch (format.format()) {
            case BHD_SAVINGS -> new BhdSavingsParser();
            case BHD_CREDIT_CARD -> new BhdCreditCardParser();
            default -> null;
        };
    }

    private void validateStatement(ParsedStatement parsed, List<ImportWarning> warnings) {
        java.util.Optional<StatementFooter> footerOpt = parsed.footer();
        java.util.Optional<java.math.BigDecimal> openingOpt = parsed.header().openingBalance();
        if (footerOpt.isEmpty() || openingOpt.isEmpty()) {
            return;
        }
        StatementFooter footer = footerOpt.get();
        java.math.BigDecimal opening = openingOpt.get();
        var expected = opening.add(footer.totalCredits()).subtract(footer.totalDebits());
        var diff = expected.subtract(footer.closingBalance()).abs();
        if (diff.compareTo(new java.math.BigDecimal("0.02")) > 0) {
            warnings.add(new ImportWarning.BalanceMismatch(
                    "Expected balance: " + expected + ", actual: " + footer.closingBalance()));
        }
    }

    private Transaction applyCategory(Transaction tx, String hash) {
        CategoryAssignment assignment = categorizationEngine.assign(tx);
        return new Transaction(
                tx.id(), tx.accountId(), tx.date(), tx.description(),
                tx.amount(), tx.direction(), tx.bank(), tx.transactionType(),
                java.util.Optional.of(assignment.category().name()),
                tx.tags(), tx.isInternalTransfer(), tx.issues(), hash
        );
    }

    private String getFilename(ImportSource source) {
        if (source instanceof ImportSource.LocalFile lf) {
            return lf.path().getFileName().toString();
        }
        if (source instanceof ImportSource.GmailAttachment ga) {
            return ga.filename();
        }
        return "unknown";
    }

    private AccountAssignment getAccount(ImportSource source) {
        if (source instanceof ImportSource.LocalFile lf) {
            return lf.account();
        }
        if (source instanceof ImportSource.GmailAttachment ga) {
            return ga.account();
        }
        return new AccountAssignment("unknown", Bank.BHD, AccountKind.SAVINGS);
    }

    private byte[] readBytes(ImportSource source) throws IOException {
        if (source instanceof ImportSource.LocalFile lf) {
            return Files.readAllBytes(lf.path());
        }
        if (source instanceof ImportSource.GmailAttachment ga) {
            return ga.bytes();
        }
        return new byte[0];
    }

    private String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
