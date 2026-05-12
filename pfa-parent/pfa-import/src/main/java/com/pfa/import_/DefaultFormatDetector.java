package com.pfa.import_;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.pfa.core.ExtractionMode;
import com.pfa.core.FormatDescriptor;
import com.pfa.core.FormatDetector;
import com.pfa.core.SourceFormat;

/**
 * Default implementation of FormatDetector.
 * Detects file type via magic bytes, probes text layer quality,
 * and matches BHD statement signatures.
 */
public class DefaultFormatDetector implements FormatDetector {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final int MIN_TEXT_LENGTH = 50;
    private static final Pattern ALPHABETIC_RUN = Pattern.compile("[a-zA-ZáéíóúñÁÉÍÓÚÑ]{3,}");

    // BHD Savings signature anchors
    private static final String SAVINGS_ANCHOR_1 = "Estado de Cuenta";
    private static final String SAVINGS_ANCHOR_2 = "Numero de Cuenta Regional";
    private static final String SAVINGS_ANCHOR_3 = "Moneda";

    // BHD Credit Card signature anchors
    private static final String CREDIT_CARD_ANCHOR = "Estado de Cuenta de Tarjeta de Cr\u00e9dito";
    private static final String CREDIT_CARD_USD_SECTION = "TRANSACCIONES EN DOLARES US$";
    private static final String CREDIT_CARD_DOP_SECTION = "TRANSACCIONES EN PESOS RD$";

    private static final double BHD_CONFIDENCE = 0.95;
    private static final double CSV_CONFIDENCE = 0.90;
    private static final double UNKNOWN_CONFIDENCE = 0.5;

    private String pdfPassword;

    public void setPdfPassword(String password) {
        this.pdfPassword = password;
    }

    @Override
    public FormatDescriptor detect(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) {
            return new FormatDescriptor(SourceFormat.UNKNOWN, ExtractionMode.TEXT_LAYER, UNKNOWN_CONFIDENCE, Map.of());
        }

        if (isPdf(bytes)) {
            return detectPdfFormat(bytes, filename);
        }

        if (isCsvHeuristic(bytes)) {
            return new FormatDescriptor(SourceFormat.CSV_GENERIC, ExtractionMode.CSV, CSV_CONFIDENCE,
                    Map.of("filename", filename != null ? filename : ""));
        }

        return new FormatDescriptor(SourceFormat.UNKNOWN, ExtractionMode.TEXT_LAYER, UNKNOWN_CONFIDENCE, Map.of());
    }

    private boolean isPdf(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isCsvHeuristic(byte[] bytes) {
        // Check if content is mostly printable ASCII with common CSV delimiters
        int printableCount = 0;
        boolean hasDelimiter = false;
        boolean hasNewline = false;
        int checkLength = Math.min(bytes.length, 4096);

        for (int i = 0; i < checkLength; i++) {
            byte b = bytes[i];
            if (b >= 0x20 && b <= 0x7E) {
                printableCount++;
                if (b == ',' || b == ';' || b == '\t' || b == '|') {
                    hasDelimiter = true;
                }
            } else if (b == '\n' || b == '\r') {
                printableCount++;
                hasNewline = true;
            }
        }

        double printableRatio = (double) printableCount / checkLength;
        return printableRatio > 0.90 && hasDelimiter && hasNewline;
    }

    private FormatDescriptor detectPdfFormat(byte[] bytes, String filename) {
        String extractedText = extractTextFromPdf(bytes, 3);
        Map<String, String> hints = new HashMap<>();
        hints.put("filename", filename != null ? filename : "");

        // Check if text layer is usable
        if (!hasUsableTextLayer(extractedText)) {
            hints.put("reason", "insufficient text layer");
            return new FormatDescriptor(SourceFormat.UNKNOWN, ExtractionMode.OCR_REQUIRED, UNKNOWN_CONFIDENCE, hints);
        }

        hints.put("textLength", String.valueOf(extractedText.length()));

        // Try BHD savings signature
        if (matchesBhdSavings(extractedText)) {
            return new FormatDescriptor(SourceFormat.BHD_SAVINGS, ExtractionMode.TEXT_LAYER, BHD_CONFIDENCE, hints);
        }

        // Try BHD credit card signature
        if (matchesBhdCreditCard(extractedText)) {
            return new FormatDescriptor(SourceFormat.BHD_CREDIT_CARD, ExtractionMode.TEXT_LAYER, BHD_CONFIDENCE, hints);
        }

        // PDF with text but unrecognized format
        return new FormatDescriptor(SourceFormat.UNKNOWN, ExtractionMode.TEXT_LAYER, UNKNOWN_CONFIDENCE, hints);
    }

    /**
     * Extracts text from the first N pages of a PDF.
     */
    private String extractTextFromPdf(byte[] bytes, int maxPages) {
        try (PDDocument document = (pdfPassword != null && !pdfPassword.isEmpty())
                ? Loader.loadPDF(bytes, pdfPassword)
                : Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(totalPages, maxPages));
            return stripper.getText(document);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Checks if the extracted text is usable (not a scanned PDF).
     * Text is usable if length >= 50 AND contains at least one alphabetic run of 3+ chars.
     */
    private boolean hasUsableTextLayer(String text) {
        if (text == null || text.length() < MIN_TEXT_LENGTH) {
            return false;
        }
        return ALPHABETIC_RUN.matcher(text).find();
    }

    private boolean matchesBhdSavings(String text) {
        String upper = text.toUpperCase();
        return upper.contains(SAVINGS_ANCHOR_1.toUpperCase())
                && upper.contains(SAVINGS_ANCHOR_2.toUpperCase())
                && upper.contains(SAVINGS_ANCHOR_3.toUpperCase());
    }

    private boolean matchesBhdCreditCard(String text) {
        String upper = text.toUpperCase();
        // Primary detection: header anchor + section header
        boolean hasHeader = upper.contains(CREDIT_CARD_ANCHOR.toUpperCase());
        boolean hasUsdSection = upper.contains(CREDIT_CARD_USD_SECTION.toUpperCase());
        boolean hasDopSection = upper.contains(CREDIT_CARD_DOP_SECTION.toUpperCase());

        if (hasHeader && (hasUsdSection || hasDopSection)) {
            return true;
        }

        // Fallback: transaction section headers alone are sufficient
        // (some PDFs don't include the full header in extracted text)
        return hasUsdSection || hasDopSection;
    }
}
