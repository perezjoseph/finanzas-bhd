package com.pfa.core;

import java.util.Objects;

/**
 * OCR output for a single page.
 */
public record OcrPage(int pageNumber, String text, double averageConfidence) {

    public OcrPage {
        Objects.requireNonNull(text, "text must not be null");
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must be non-negative, got " + pageNumber);
        }
        if (averageConfidence < 0.0 || averageConfidence > 1.0) {
            throw new IllegalArgumentException("averageConfidence must be between 0.0 and 1.0, got " + averageConfidence);
        }
    }
}
