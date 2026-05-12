package com.pfa.core;

import java.util.Optional;

/**
 * Options controlling the import process.
 */
public record ImportOptions(boolean skipDuplicateCheck, Optional<String> pdfPassword) {

    /** Default import options (no password). */
    public static ImportOptions defaults() {
        return new ImportOptions(false, Optional.empty());
    }

    /** Import options with a PDF password for encrypted statements. */
    public static ImportOptions withPassword(String password) {
        return new ImportOptions(false, Optional.ofNullable(password));
    }
}
