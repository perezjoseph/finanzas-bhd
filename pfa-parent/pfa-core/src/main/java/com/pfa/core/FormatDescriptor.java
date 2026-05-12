package com.pfa.core;

import java.util.Map;
import java.util.Objects;

/**
 * Describes the detected format of a source file, including extraction mode and confidence.
 */
public record FormatDescriptor(
        SourceFormat format,
        ExtractionMode mode,
        double confidence,
        Map<String, String> hints
) {

    public FormatDescriptor {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0, got " + confidence);
        }
        hints = hints != null ? Map.copyOf(hints) : Map.of();
    }
}
