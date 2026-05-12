package com.pfa.core;

import java.nio.file.Path;

/**
 * Exports transaction data to CSV or Excel format.
 */
public interface ExportEngine {

    /**
     * Exports the given transactions to a CSV file at the target path.
     *
     * @throws ExportException if the export fails (e.g., file system error)
     */
    void exportCsv(Path target, TransactionSet txs, FilterCriteria activeFilter) throws ExportException;

    /**
     * Exports the given transactions to an Excel file at the target path.
     *
     * @throws ExportException if the export fails (e.g., file system error)
     */
    void exportExcel(Path target, TransactionSet txs, FilterCriteria activeFilter) throws ExportException;
}
