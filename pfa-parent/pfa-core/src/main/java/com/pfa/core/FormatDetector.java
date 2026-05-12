package com.pfa.core;

/**
 * Detects the format of a source file given its raw bytes and filename.
 * Pure function of (bytes, filename) — no I/O or network calls.
 */
public interface FormatDetector {

    /**
     * Analyzes the given bytes and filename to determine the source format,
     * extraction mode, and confidence score.
     */
    FormatDescriptor detect(byte[] bytes, String filename);
}
