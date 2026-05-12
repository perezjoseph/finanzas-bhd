package com.pfa.core;

import java.util.Optional;

/**
 * Options for OCR processing, such as language hints, DPI overrides,
 * or preprocessing toggles.
 */
public record OcrOptions(
        Optional<String> languageHint,
        Optional<Integer> dpiOverride
) {

    public OcrOptions {
        java.util.Objects.requireNonNull(languageHint, "languageHint must not be null");
        java.util.Objects.requireNonNull(dpiOverride, "dpiOverride must not be null");
    }

    /** Default options with no overrides. */
    public static OcrOptions defaults() {
        return new OcrOptions(Optional.empty(), Optional.empty());
    }
}
