package com.pfa.persistence;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Derives encryption keys from passwords using Argon2id.
 * <p>
 * Parameters per design spec: m=65536 (64 MB), t=3 iterations, p=4 parallelism.
 */
public class KeyDerivationService {

    private static final int KEY_LENGTH_BYTES = 32; // AES-256
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int MEMORY_KB = 65536;     // 64 MB
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 4;

    private final java.security.SecureRandom secureRandom;

    public KeyDerivationService() {
        this.secureRandom = new java.security.SecureRandom();
    }

    /**
     * Derives a 256-bit key from the given password and salt using Argon2id.
     *
     * @param password the vault password
     * @param salt     a 16-byte salt (must be stored alongside the encrypted data)
     * @return a 32-byte derived key suitable for AES-256
     */
    public byte[] deriveKey(char[] password, byte[] salt) {
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(salt, "salt must not be null");
        if (password.length == 0) {
            throw new IllegalArgumentException("password must not be empty");
        }

        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(MEMORY_KB)
                .withIterations(ITERATIONS)
                .withParallelism(PARALLELISM)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] key = new byte[KEY_LENGTH_BYTES];
        byte[] passwordBytes = new String(password).getBytes(StandardCharsets.UTF_8);
        try {
            generator.generateBytes(passwordBytes, key);
        } finally {
            // Clear password bytes from memory
            java.util.Arrays.fill(passwordBytes, (byte) 0);
        }

        return key;
    }

    /**
     * Generates a random salt for key derivation.
     * The salt must be stored alongside the encrypted data to allow decryption.
     */
    public byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        return salt;
    }
}
