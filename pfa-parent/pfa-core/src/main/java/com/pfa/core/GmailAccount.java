package com.pfa.core;

import java.util.Objects;

/**
 * Gmail account credentials for IMAP access.
 */
public record GmailAccount(String email, char[] appPassword) {

    public GmailAccount {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(appPassword, "appPassword must not be null");
    }
}
