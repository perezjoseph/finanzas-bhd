package com.pfa.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * Manages vault mode: password-protected access with lockout after failed attempts.
 * Tracks failed authentication attempts and locks for 60 seconds after 5 consecutive failures.
 */
public class VaultManager {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 60;

    private String vaultPasswordHash;
    private boolean vaultEnabled;
    private int failedAttempts;
    private Instant lockoutUntil;

    public VaultManager() {
        this.vaultEnabled = false;
        this.failedAttempts = 0;
        this.lockoutUntil = null;
    }

    /**
     * Enables vault mode with the given password.
     */
    public void enableVault(String password) {
        Objects.requireNonNull(password, "password must not be null");
        this.vaultPasswordHash = hashPassword(password);
        this.vaultEnabled = true;
        this.failedAttempts = 0;
        this.lockoutUntil = null;
    }

    /**
     * Disables vault mode. Requires correct password.
     */
    public boolean disableVault(String password) {
        if (!authenticate(password)) {
            return false;
        }
        this.vaultEnabled = false;
        this.vaultPasswordHash = null;
        return true;
    }

    /**
     * Attempts to authenticate with the given password.
     * Returns true if successful, false if wrong password or locked out.
     */
    public boolean authenticate(String password) {
        if (!vaultEnabled) {
            return true;
        }

        if (isLockedOut()) {
            return false;
        }

        if (verifyPassword(password)) {
            failedAttempts = 0;
            lockoutUntil = null;
            return true;
        }

        failedAttempts++;
        if (failedAttempts >= MAX_ATTEMPTS) {
            lockoutUntil = Instant.now().plusSeconds(LOCKOUT_DURATION_SECONDS);
        }
        return false;
    }

    /**
     * Returns true if the vault is currently locked out due to too many failed attempts.
     */
    public boolean isLockedOut() {
        if (lockoutUntil == null) {
            return false;
        }
        if (Instant.now().isAfter(lockoutUntil)) {
            // Lockout expired
            lockoutUntil = null;
            failedAttempts = 0;
            return false;
        }
        return true;
    }

    public boolean isVaultEnabled() {
        return vaultEnabled;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    /**
     * Returns the number of seconds remaining in the lockout period, or 0 if not locked out.
     */
    public long getLockoutRemainingSeconds() {
        if (lockoutUntil == null) {
            return 0;
        }
        long remaining = java.time.Duration.between(Instant.now(), lockoutUntil).getSeconds();
        if (remaining <= 0) {
            lockoutUntil = null;
            failedAttempts = 0;
            return 0;
        }
        return remaining;
    }

    private boolean verifyPassword(String password) {
        if (password == null || vaultPasswordHash == null) {
            return false;
        }
        return hashPassword(password).equals(vaultPasswordHash);
    }

    /**
     * Simple hash for vault password. In production, use Argon2id.
     * This is a placeholder using SHA-256.
     */
    private String hashPassword(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
