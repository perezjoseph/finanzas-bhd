package com.pfa.persistence;

import java.util.Arrays;
import java.util.Objects;

/**
 * Coordinates encryption/decryption of session data, managing key retrieval
 * based on whether vault mode is enabled or disabled.
 * <p>
 * When vault mode is enabled: derives the key from the user's password via Argon2id.
 * When vault mode is disabled: retrieves/stores the key via Windows DPAPI.
 * <p>
 * Encrypted file format:
 * <pre>
 * [1 byte: version] [1 byte: mode (0=DPAPI, 1=vault)] [16 bytes: salt (vault only)] [encrypted payload]
 * </pre>
 */
public class SessionEncryptionManager {

    private static final byte FORMAT_VERSION = 1;
    private static final byte MODE_DPAPI = 0;
    private static final byte MODE_VAULT = 1;
    private static final int SALT_LENGTH = 16;

    private final EncryptionService encryptionService;
    private final KeyDerivationService keyDerivationService;
    private final DpapiKeyStore dpapiKeyStore;

    public SessionEncryptionManager(EncryptionService encryptionService,
                                    KeyDerivationService keyDerivationService,
                                    DpapiKeyStore dpapiKeyStore) {
        this.encryptionService = Objects.requireNonNull(encryptionService);
        this.keyDerivationService = Objects.requireNonNull(keyDerivationService);
        this.dpapiKeyStore = Objects.requireNonNull(dpapiKeyStore);
    }

    /**
     * Encrypts session data using DPAPI-stored key (vault mode disabled).
     * If no key exists yet, generates one and stores it via DPAPI.
     *
     * @param plaintext the session data to encrypt
     * @return the encrypted data with header
     * @throws EncryptionException if encryption fails
     */
    public byte[] encryptWithDpapi(byte[] plaintext) throws EncryptionException {
        Objects.requireNonNull(plaintext, "plaintext must not be null");

        byte[] key = dpapiKeyStore.retrieveKey();
        if (key == null) {
            key = encryptionService.generateKey();
            dpapiKeyStore.storeKey(key);
        }

        try {
            byte[] encrypted = encryptionService.encrypt(plaintext, key);

            // Build output: [version][mode][encrypted payload]
            byte[] result = new byte[2 + encrypted.length];
            result[0] = FORMAT_VERSION;
            result[1] = MODE_DPAPI;
            System.arraycopy(encrypted, 0, result, 2, encrypted.length);

            return result;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * Encrypts session data using a key derived from the vault password.
     *
     * @param plaintext the session data to encrypt
     * @param password  the vault password
     * @return the encrypted data with header (includes salt for key derivation)
     * @throws EncryptionException if encryption fails
     */
    public byte[] encryptWithVaultPassword(byte[] plaintext, char[] password) throws EncryptionException {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        Objects.requireNonNull(password, "password must not be null");

        byte[] salt = keyDerivationService.generateSalt();
        byte[] key = keyDerivationService.deriveKey(password, salt);

        try {
            byte[] encrypted = encryptionService.encrypt(plaintext, key);

            // Build output: [version][mode][salt (16 bytes)][encrypted payload]
            byte[] result = new byte[2 + SALT_LENGTH + encrypted.length];
            result[0] = FORMAT_VERSION;
            result[1] = MODE_VAULT;
            System.arraycopy(salt, 0, result, 2, SALT_LENGTH);
            System.arraycopy(encrypted, 0, result, 2 + SALT_LENGTH, encrypted.length);

            return result;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * Decrypts session data. Automatically detects the mode from the header.
     *
     * @param encrypted the encrypted data (with header)
     * @param password  the vault password (required if mode is vault, may be null for DPAPI mode)
     * @return the decrypted plaintext
     * @throws EncryptionException if decryption fails
     */
    public byte[] decrypt(byte[] encrypted, char[] password) throws EncryptionException {
        Objects.requireNonNull(encrypted, "encrypted data must not be null");

        if (encrypted.length < 2) {
            throw new EncryptionException("Encrypted data too short: missing header");
        }

        byte version = encrypted[0];
        if (version != FORMAT_VERSION) {
            throw new EncryptionException("Unsupported encryption format version: " + version);
        }

        byte mode = encrypted[1];
        return switch (mode) {
            case MODE_DPAPI -> decryptDpapi(encrypted);
            case MODE_VAULT -> decryptVault(encrypted, password);
            default -> throw new EncryptionException("Unknown encryption mode: " + mode);
        };
    }

    /**
     * Returns true if the given data appears to be encrypted (has a valid header).
     */
    public boolean isEncrypted(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        return data[0] == FORMAT_VERSION && (data[1] == MODE_DPAPI || data[1] == MODE_VAULT);
    }

    /**
     * Returns true if the encrypted data uses vault mode.
     */
    public boolean isVaultMode(byte[] encrypted) {
        if (encrypted == null || encrypted.length < 2) {
            return false;
        }
        return encrypted[0] == FORMAT_VERSION && encrypted[1] == MODE_VAULT;
    }

    private byte[] decryptDpapi(byte[] encrypted) throws EncryptionException {
        byte[] key = dpapiKeyStore.retrieveKey();
        if (key == null) {
            throw new EncryptionException("No DPAPI key found — cannot decrypt session data");
        }

        try {
            byte[] payload = Arrays.copyOfRange(encrypted, 2, encrypted.length);
            return encryptionService.decrypt(payload, key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private byte[] decryptVault(byte[] encrypted, char[] password) throws EncryptionException {
        if (password == null || password.length == 0) {
            throw new EncryptionException("Vault password required to decrypt session data");
        }

        if (encrypted.length < 2 + SALT_LENGTH) {
            throw new EncryptionException("Encrypted data too short: missing salt");
        }

        byte[] salt = Arrays.copyOfRange(encrypted, 2, 2 + SALT_LENGTH);
        byte[] payload = Arrays.copyOfRange(encrypted, 2 + SALT_LENGTH, encrypted.length);

        byte[] key = keyDerivationService.deriveKey(password, salt);
        try {
            return encryptionService.decrypt(payload, key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }
}
