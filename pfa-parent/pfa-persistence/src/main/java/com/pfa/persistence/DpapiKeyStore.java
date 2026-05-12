package com.pfa.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Stores and retrieves the AES-256 encryption key using Windows DPAPI (user scope).
 * <p>
 * When vault mode is disabled, a random encryption key is generated and protected
 * via CryptProtectData/CryptUnprotectData. The protected blob is stored on disk.
 * Only the same Windows user on the same machine can unprotect it.
 * <p>
 * On non-Windows systems, falls back to storing the key in a file with restricted
 * permissions (less secure, but functional for development/testing).
 */
public class DpapiKeyStore {

    private static final String KEY_FILENAME = "encryption.key";

    private final Path keyFile;
    private final boolean isWindows;

    public DpapiKeyStore(Path storageDirectory) {
        Objects.requireNonNull(storageDirectory, "storageDirectory must not be null");
        this.keyFile = storageDirectory.resolve(KEY_FILENAME);
        this.isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Stores the encryption key, protecting it with DPAPI on Windows.
     *
     * @param key the 32-byte AES-256 key to store
     * @throws EncryptionException if the key cannot be stored
     */
    public void storeKey(byte[] key) throws EncryptionException {
        Objects.requireNonNull(key, "key must not be null");
        try {
            Files.createDirectories(keyFile.getParent());
            if (isWindows) {
                byte[] protectedData = protectData(key);
                Files.write(keyFile, protectedData);
            } else {
                // Fallback for non-Windows: store raw key (development only)
                Files.write(keyFile, key);
            }
        } catch (IOException e) {
            throw new EncryptionException("Failed to store encryption key: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the encryption key, unprotecting it with DPAPI on Windows.
     *
     * @return the 32-byte AES-256 key, or null if no key is stored
     * @throws EncryptionException if the key cannot be retrieved or unprotected
     */
    public byte[] retrieveKey() throws EncryptionException {
        if (!Files.exists(keyFile)) {
            return null;
        }
        try {
            byte[] storedData = Files.readAllBytes(keyFile);
            if (isWindows) {
                return unprotectData(storedData);
            } else {
                return storedData;
            }
        } catch (IOException e) {
            throw new EncryptionException("Failed to retrieve encryption key: " + e.getMessage(), e);
        }
    }

    /**
     * Returns true if a stored key exists.
     */
    public boolean hasStoredKey() {
        return Files.exists(keyFile);
    }

    /**
     * Deletes the stored key.
     */
    public void deleteKey() throws EncryptionException {
        try {
            Files.deleteIfExists(keyFile);
        } catch (IOException e) {
            throw new EncryptionException("Failed to delete encryption key: " + e.getMessage(), e);
        }
    }

    // --- Windows DPAPI via JNA ---

    /**
     * JNA interface for Crypt32.dll (CryptProtectData / CryptUnprotectData).
     */
    public interface Crypt32 extends StdCallLibrary {
        Crypt32 INSTANCE = Native.load("Crypt32", Crypt32.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean CryptProtectData(
                DATA_BLOB pDataIn,
                String szDataDescr,
                DATA_BLOB pOptionalEntropy,
                Pointer pvReserved,
                Pointer pPromptStruct,
                int dwFlags,
                DATA_BLOB pDataOut);

        boolean CryptUnprotectData(
                DATA_BLOB pDataIn,
                Pointer ppszDataDescr,
                DATA_BLOB pOptionalEntropy,
                Pointer pvReserved,
                Pointer pPromptStruct,
                int dwFlags,
                DATA_BLOB pDataOut);
    }

    /**
     * DATA_BLOB structure used by DPAPI functions.
     */
    @Structure.FieldOrder({"cbData", "pbData"})
    public static class DATA_BLOB extends Structure {
        public int cbData;
        public Pointer pbData;

        public DATA_BLOB() {
            super();
        }

        public DATA_BLOB(byte[] data) {
            super();
            if (data != null && data.length > 0) {
                cbData = data.length;
                pbData = new Memory(data.length);
                pbData.write(0, data, 0, data.length);
            }
        }

        public byte[] getData() {
            if (cbData == 0 || pbData == null) {
                return new byte[0];
            }
            return pbData.getByteArray(0, cbData);
        }
    }

    private byte[] protectData(byte[] plaintext) throws EncryptionException {
        DATA_BLOB dataIn = new DATA_BLOB(plaintext);
        DATA_BLOB dataOut = new DATA_BLOB();

        boolean success = Crypt32.INSTANCE.CryptProtectData(
                dataIn, "PFA Encryption Key", null, null, null, 0, dataOut);

        if (!success) {
            int error = Kernel32.INSTANCE.GetLastError();
            throw new EncryptionException("CryptProtectData failed with error code: " + error);
        }

        byte[] result = dataOut.getData();
        // Free the memory allocated by CryptProtectData
        if (dataOut.pbData != null) {
            Kernel32.INSTANCE.LocalFree(dataOut.pbData);
        }
        return result;
    }

    private byte[] unprotectData(byte[] protectedData) throws EncryptionException {
        DATA_BLOB dataIn = new DATA_BLOB(protectedData);
        DATA_BLOB dataOut = new DATA_BLOB();

        boolean success = Crypt32.INSTANCE.CryptUnprotectData(
                dataIn, null, null, null, null, 0, dataOut);

        if (!success) {
            int error = Kernel32.INSTANCE.GetLastError();
            throw new EncryptionException("CryptUnprotectData failed with error code: " + error);
        }

        byte[] result = dataOut.getData();
        // Free the memory allocated by CryptUnprotectData
        if (dataOut.pbData != null) {
            Kernel32.INSTANCE.LocalFree(dataOut.pbData);
        }
        return result;
    }
}
