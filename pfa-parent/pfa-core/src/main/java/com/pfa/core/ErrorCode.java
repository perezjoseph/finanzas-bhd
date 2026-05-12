package com.pfa.core;

/**
 * Machine-readable error codes for import failures.
 * Used in {@link ImportError} to identify the type of failure without parsing messages.
 */
public enum ErrorCode {
    CORRUPTED_FILE,
    UNREADABLE_PDF,
    FILE_TOO_LARGE,
    INVALID_FORMAT,
    DUPLICATE_FILE,
    NO_TRANSACTIONS,
    PARSE_FAILED,
    OCR_FAILED,
    UNSUPPORTED_FORMAT
}
