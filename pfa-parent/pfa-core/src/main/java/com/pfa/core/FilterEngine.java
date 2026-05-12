package com.pfa.core;

import java.util.Collection;

/**
 * Applies filter criteria to a collection of transactions,
 * producing a filtered TransactionSet.
 */
public interface FilterEngine {

    /**
     * Filters the given transactions using the specified criteria.
     * An empty or null criteria matches everything.
     */
    TransactionSet apply(Collection<Transaction> all, FilterCriteria criteria);
}
