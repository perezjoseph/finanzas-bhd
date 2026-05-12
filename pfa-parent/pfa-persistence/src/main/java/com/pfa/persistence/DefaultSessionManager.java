package com.pfa.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.pfa.core.SessionHandle;
import com.pfa.core.SessionManager;
import com.pfa.core.SessionSnapshot;

/**
 * Default implementation of SessionManager.
 * Saves and loads analysis sessions as encrypted JSON .pfa files with atomic writes
 * and schema version checking.
 * <p>
 * Encryption behavior:
 * - When vault mode is enabled: encrypts with key derived from vault password via Argon2id
 * - When vault mode is disabled: encrypts with a DPAPI-protected random key
 * - On encryption failure: aborts save, retains previous version
 */
public class DefaultSessionManager implements SessionManager {

    private static final String SESSION_EXTENSION = ".pfa";
    private static final String SESSIONS_DIR = "sessions";
    static final String SCHEMA_VERSION = "1.0.0";

    private final Path sessionsDirectory;
    private final SnapshotSerializer serializer;
    private final SessionEncryptionManager encryptionManager;
    private final VaultManager vaultManager;

    /** The vault password for the current session (held in memory while app is open). */
    private char[] activeVaultPassword;

    public DefaultSessionManager(Path baseDirectory) {
        this(baseDirectory, null, null);
    }

    public DefaultSessionManager(Path baseDirectory,
                                 SessionEncryptionManager encryptionManager,
                                 VaultManager vaultManager) {
        this.sessionsDirectory = Objects.requireNonNull(baseDirectory).resolve(SESSIONS_DIR);
        this.serializer = new SnapshotSerializer();
        this.encryptionManager = encryptionManager;
        this.vaultManager = vaultManager;
    }

    /**
     * Sets the active vault password for encryption/decryption.
     * Called after successful vault authentication.
     */
    public void setActiveVaultPassword(char[] password) {
        this.activeVaultPassword = password;
    }

    @Override
    public SessionHandle save(String name, SessionSnapshot snapshot) {
        validateName(name);
        try {
            Files.createDirectories(sessionsDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create sessions directory", e);
        }

        Path target = sessionsDirectory.resolve(sanitizeName(name) + SESSION_EXTENSION);
        Path temp = sessionsDirectory.resolve(sanitizeName(name) + ".tmp");

        // Serialize to JSON bytes
        byte[] data = serializer.serialize(snapshot);

        // Encrypt the data if encryption is available
        byte[] dataToWrite = encryptData(data, target);

        // Atomic save: write to temp file first, then move
        try {
            Files.write(temp, dataToWrite);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write temp session file: " + name, e);
        }

        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // ATOMIC_MOVE may not be supported on all filesystems; fall back to REPLACE_EXISTING only
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                throw new UncheckedIOException("Failed to finalize session file: " + name, e2);
            }
        }

        return new SessionHandle(name, target, snapshot.schemaVersion(), Instant.now());
    }

    @Override
    public SessionSnapshot load(SessionHandle handle) {
        Path sessionFile = handle.file();
        if (!Files.exists(sessionFile)) {
            throw new UncheckedIOException(new IOException("Session file not found: " + handle.name()));
        }

        try {
            byte[] rawData = Files.readAllBytes(sessionFile);

            // Decrypt if encryption is available
            byte[] data = decryptData(rawData);

            SessionSnapshot snapshot = serializer.deserialize(data);

            // Schema version check
            if (!isCompatibleVersion(snapshot.schemaVersion())) {
                throw new IOException(
                        "Incompatible session schema version: " + snapshot.schemaVersion()
                                + " (expected compatible with " + SCHEMA_VERSION + ")");
            }

            return snapshot;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load session: " + handle.name(), e);
        }
    }

    @Override
    public List<SessionHandle> list() {
        if (!Files.exists(sessionsDirectory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(sessionsDirectory)) {
            return paths
                    .filter(p -> p.toString().endsWith(SESSION_EXTENSION))
                    .map(this::toSessionHandle)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list sessions", e);
        }
    }

    @Override
    public void delete(SessionHandle handle) {
        try {
            Files.deleteIfExists(handle.file());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete session: " + handle.name(), e);
        }
    }

    private SessionHandle toSessionHandle(Path path) {
        String filename = path.getFileName().toString();
        String name = filename.substring(0, filename.length() - SESSION_EXTENSION.length());
        Instant lastModified;
        try {
            lastModified = Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            lastModified = Instant.EPOCH;
        }

        // Try to read schema version from the file header
        String version = SCHEMA_VERSION;
        try {
            byte[] data = Files.readAllBytes(path);
            String json = new String(data, StandardCharsets.UTF_8);
            // Quick extraction of schemaVersion without full deserialization
            int idx = json.indexOf("\"schemaVersion\"");
            if (idx >= 0) {
                int valueStart = json.indexOf('"', idx + 15);
                if (valueStart >= 0) {
                    int valueEnd = json.indexOf('"', valueStart + 1);
                    if (valueEnd >= 0) {
                        version = json.substring(valueStart + 1, valueEnd);
                    }
                }
            }
        } catch (IOException ignored) {
            // Use default version if we can't read the file
        }

        return new SessionHandle(name, path, version, lastModified);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Session name must not be blank");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("Session name must be at most 100 characters");
        }
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-\\s]", "_").trim();
    }

    /**
     * Checks if the given schema version is compatible with the current version.
     * Compatible means the major version matches (semver major compatibility).
     */
    private boolean isCompatibleVersion(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        try {
            int fileMajor = Integer.parseInt(version.split("\\.")[0]);
            int currentMajor = Integer.parseInt(SCHEMA_VERSION.split("\\.")[0]);
            return fileMajor == currentMajor;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    /**
     * Encrypts serialized data before writing to disk.
     * On encryption failure, aborts and retains the previous version (Requirement 12.2).
     *
     * @param data   the serialized plaintext
     * @param target the target file path (used to check if previous version exists)
     * @return encrypted bytes, or the original data if encryption is not configured
     */
    private byte[] encryptData(byte[] data, Path target) {
        if (encryptionManager == null) {
            return data;
        }

        try {
            if (vaultManager != null && vaultManager.isVaultEnabled() && activeVaultPassword != null) {
                return encryptionManager.encryptWithVaultPassword(data, activeVaultPassword);
            } else {
                return encryptionManager.encryptWithDpapi(data);
            }
        } catch (EncryptionException e) {
            // Abort save on encryption failure, retain previous version (Requirement 12.2)
            if (Files.exists(target)) {
                throw new UncheckedIOException(
                        new IOException("Encryption failed — previous session retained: " + e.getMessage(), e));
            }
            throw new UncheckedIOException(
                    new IOException("Encryption failed — data could not be saved securely: " + e.getMessage(), e));
        }
    }

    /**
     * Decrypts data read from disk.
     * If encryption is not configured, returns the raw data as-is.
     *
     * @param rawData the raw bytes from the session file
     * @return decrypted plaintext
     */
    private byte[] decryptData(byte[] rawData) throws IOException {
        if (encryptionManager == null) {
            return rawData;
        }

        // Check if data is actually encrypted
        if (!encryptionManager.isEncrypted(rawData)) {
            // Legacy unencrypted file — return as-is
            return rawData;
        }

        try {
            return encryptionManager.decrypt(rawData, activeVaultPassword);
        } catch (EncryptionException e) {
            throw new IOException("Decryption failed: " + e.getMessage(), e);
        }
    }
}
