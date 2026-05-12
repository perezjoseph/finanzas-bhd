package com.pfa.core;

import java.util.List;

/**
 * Entry point for all statement ingestion. Accepts a batch of source descriptors,
 * coordinates concurrent parsing, deduplicates, and aggregates results.
 */
public interface StatementImporter {

    /**
     * Imports all provided sources, returning an aggregated result with
     * successes, failures, warnings, duplicates, and empty files.
     */
    ImportBatchResult importAll(List<ImportSource> sources, ImportOptions options);
}
