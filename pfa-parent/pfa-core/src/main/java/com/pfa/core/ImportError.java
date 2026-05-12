package com.pfa.core;

import java.util.Objects;

/**
 * Describes an import failure with a machine-readable code and a human-readable message.
 * Errors never throw across module boundaries — they are captured as ImportError records.
 *
 * @param fileName the name of the file that caused the error
 * @param code     machine-readable error classification
 * @param message  human-readable localized description of the failure
 */
public record ImportError(String fileName, ErrorCode code, String message) {

    public ImportError {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
