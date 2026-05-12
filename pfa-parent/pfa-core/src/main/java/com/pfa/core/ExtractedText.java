package com.pfa.core;

import java.util.List;
import java.util.Objects;

/**
 * Text extracted from a document, organized by page.
 */
public record ExtractedText(List<String> pages) {

    public ExtractedText {
        Objects.requireNonNull(pages, "pages must not be null");
        pages = List.copyOf(pages);
    }
}
