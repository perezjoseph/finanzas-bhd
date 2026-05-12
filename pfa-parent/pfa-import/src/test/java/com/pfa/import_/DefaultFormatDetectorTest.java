package com.pfa.import_;

import com.pfa.core.ExtractionMode;
import com.pfa.core.FormatDescriptor;
import com.pfa.core.SourceFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import static org.junit.jupiter.api.Assertions.*;

class DefaultFormatDetectorTest {

    private DefaultFormatDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DefaultFormatDetector();
    }

    // --- PDF Detection ---

    @Test
    void detectsPdfByMagicBytes() throws IOException {
        byte[] pdfBytes = createPdfWithText("Some generic PDF content with enough text to pass the threshold check easily.");
        FormatDescriptor result = detector.detect(pdfBytes, "test.pdf");

        assertNotEquals(ExtractionMode.CSV, result.mode());
        // It's a PDF, so mode should be TEXT_LAYER or OCR_REQUIRED
        assertTrue(result.mode() == ExtractionMode.TEXT_LAYER || result.mode() == ExtractionMode.OCR_REQUIRED);
    }

    // --- CSV Detection ---

    @Test
    void detectsCsvByHeuristic() {
        String csvContent = "date,description,amount,currency\n2026-01-01,MASSY STORES,99.56,USD\n2026-01-02,PAYPAL,12.00,USD\n";
        byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);

        FormatDescriptor result = detector.detect(csvBytes, "transactions.csv");

        assertEquals(SourceFormat.CSV_GENERIC, result.format());
        assertEquals(ExtractionMode.CSV, result.mode());
        assertEquals(0.90, result.confidence(), 0.001);
    }

    // --- BHD Savings Signature ---

    @Test
    void detectsBhdSavingsSignature() throws IOException {
        String savingsText = "Estado de Cuenta\n"
                + "Numero de Cuenta Regional: DO89BCBH000000000XXXXXXX0022\n"
                + "Moneda: US$\n"
                + "Fecha de Corte: 28/02/2026\n"
                + "Balance Inicial: $2,026.87\n"
                + "Some more text to ensure we have enough content for the threshold.";
        byte[] pdfBytes = createPdfWithText(savingsText);

        FormatDescriptor result = detector.detect(pdfBytes, "savings_statement.pdf");

        assertEquals(SourceFormat.BHD_SAVINGS, result.format());
        assertEquals(ExtractionMode.TEXT_LAYER, result.mode());
        assertEquals(0.95, result.confidence(), 0.001);
    }

    // --- BHD Credit Card Signature ---

    @Test
    void detectsBhdCreditCardWithUsdSection() throws IOException {
        String creditCardText = "Estado de Cuenta de Tarjeta de Cr\u00e9dito\n"
                + "VISA MI PAIS\n"
                + "Numero de tarjeta: 464133******6819\n"
                + "TRANSACCIONES EN DOLARES US$\n"
                + "Some transaction data here to fill the content threshold requirement.";
        byte[] pdfBytes = createPdfWithText(creditCardText);

        FormatDescriptor result = detector.detect(pdfBytes, "credit_card.pdf");

        assertEquals(SourceFormat.BHD_CREDIT_CARD, result.format());
        assertEquals(ExtractionMode.TEXT_LAYER, result.mode());
        assertEquals(0.95, result.confidence(), 0.001);
    }

    @Test
    void detectsBhdCreditCardWithDopSection() throws IOException {
        String creditCardText = "Estado de Cuenta de Tarjeta de Cr\u00e9dito\n"
                + "VISA MI PAIS\n"
                + "Numero de tarjeta: 464133******6819\n"
                + "TRANSACCIONES EN PESOS RD$\n"
                + "Some transaction data here to fill the content threshold requirement.";
        byte[] pdfBytes = createPdfWithText(creditCardText);

        FormatDescriptor result = detector.detect(pdfBytes, "credit_card.pdf");

        assertEquals(SourceFormat.BHD_CREDIT_CARD, result.format());
        assertEquals(ExtractionMode.TEXT_LAYER, result.mode());
        assertEquals(0.95, result.confidence(), 0.001);
    }

    // --- Unknown Format ---

    @Test
    void returnsUnknownForUnrecognizedPdf() throws IOException {
        String genericText = "This is a generic PDF document that does not match any known bank statement format. "
                + "It has enough text to pass the text layer check but no BHD signatures.";
        byte[] pdfBytes = createPdfWithText(genericText);

        FormatDescriptor result = detector.detect(pdfBytes, "random.pdf");

        assertEquals(SourceFormat.UNKNOWN, result.format());
        assertEquals(ExtractionMode.TEXT_LAYER, result.mode());
        assertTrue(result.confidence() < 0.8);
    }

    @Test
    void returnsUnknownForEmptyBytes() {
        FormatDescriptor result = detector.detect(new byte[0], "empty.pdf");

        assertEquals(SourceFormat.UNKNOWN, result.format());
        assertTrue(result.confidence() < 0.8);
    }

    @Test
    void returnsUnknownForNonPdfNonCsvBinary() {
        // Random binary data that isn't PDF or CSV
        byte[] binaryData = new byte[]{0x00, 0x01, 0x02, 0x03, (byte) 0xFF, (byte) 0xFE, 0x00, 0x01};

        FormatDescriptor result = detector.detect(binaryData, "data.bin");

        assertEquals(SourceFormat.UNKNOWN, result.format());
        assertTrue(result.confidence() < 0.8);
    }

    // --- OCR Required Detection ---

    @Test
    void detectsOcrRequiredWhenTextLayerTooShort() throws IOException {
        // Create a PDF with very little text (< 50 chars)
        byte[] pdfBytes = createPdfWithText("Hi");

        FormatDescriptor result = detector.detect(pdfBytes, "scanned.pdf");

        assertEquals(ExtractionMode.OCR_REQUIRED, result.mode());
        assertTrue(result.confidence() < 0.8);
    }

    @Test
    void detectsOcrRequiredWhenNoAlphabeticRun() throws IOException {
        // Create a PDF with numbers/symbols but no alphabetic run of 3+ chars
        String numbersOnly = "12 34 56 78 90 12 34 56 78 90 12 34 56 78 90 12 34 56 78 90 12 34 56 78";
        byte[] pdfBytes = createPdfWithText(numbersOnly);

        FormatDescriptor result = detector.detect(pdfBytes, "numbers.pdf");

        assertEquals(ExtractionMode.OCR_REQUIRED, result.mode());
    }

    // --- Helper Methods ---

    private byte[] createPdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);

                // Split text into lines to avoid going off-page
                String[] lines = text.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        contentStream.newLineAtOffset(0, -15);
                    }
                    contentStream.showText(lines[i]);
                }
                contentStream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }
}
