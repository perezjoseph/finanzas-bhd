package com.pfa.core;

/**
 * Parses extracted text from a bank statement into structured data.
 */
public interface StatementParser {

    /**
     * Parses the given extracted text using the format descriptor to guide parsing logic.
     */
    ParsedStatement parse(ExtractedText text, FormatDescriptor format);
}
