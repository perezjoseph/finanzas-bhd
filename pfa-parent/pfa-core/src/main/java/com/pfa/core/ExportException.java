package com.pfa.core;

/**
 * Checked exception thrown when an export operation fails
 * (e.g., file system errors, permission issues).
 */
public class ExportException extends Exception {

    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
