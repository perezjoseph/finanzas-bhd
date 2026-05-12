package com.pfa.core;

/**
 * Converts scanned PDF pages to text using OCR.
 * Supports GPU (CUDA) and CPU execution modes.
 */
public interface OcrEngine {

    /**
     * Extracts text from all pages of the given PDF bytes.
     */
    OcrResult extract(byte[] pdfBytes, OcrOptions options);

    /**
     * Returns the active OCR execution mode (GPU_CUDA or CPU).
     */
    OcrMode activeMode();
}
