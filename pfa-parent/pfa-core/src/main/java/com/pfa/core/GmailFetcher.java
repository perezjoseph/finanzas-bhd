package com.pfa.core;

import java.util.List;

/**
 * Fetches bank statement PDF attachments from Gmail via IMAP.
 * Only invoked on explicit user action.
 */
public interface GmailFetcher {

    /**
     * Fetches PDF attachments matching the given rules from the specified Gmail account.
     */
    FetchResult fetch(GmailAccount account, List<FetchRule> rules);

    /**
     * Saves (or updates) a Gmail account's credentials in the encrypted store.
     */
    void saveAccount(GmailAccount account);

    /**
     * Removes a Gmail account and its stored credentials.
     */
    void removeAccount(String email);

    /**
     * Returns a saved account by email, or null if not found.
     */
    GmailAccount getAccount(String email);
}
