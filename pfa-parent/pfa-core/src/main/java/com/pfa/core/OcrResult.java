package com.pfa.core;

import java.util.List;
import java.util.Objects;

/**
 * Result of OCR extraction containing per-page text and confidence scores.
 */
public record OcrResult(List<OcrPage> pages) {

    public OcrResult {
        Objects.requireNonNull(pages, "pages must not be null");
        pages = List.copyOf(pages);
    }
}
