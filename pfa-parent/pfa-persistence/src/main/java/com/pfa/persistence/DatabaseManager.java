package com.pfa.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Manages the SQLite database lifecycle: creation, connection, schema setup.
 * Stores data at %APPDATA%/PersonalFinanceAnalyzer/workspace.pfadb.
 */
public class DatabaseManager {

    private static final String DB_FILENAME = "workspace.pfadb";
    private static final String APP_DIR = "PersonalFinanceAnalyzer";

    private final Path dbPath;
    private Connection connection;

    public DatabaseManager() {
        this(defaultDbPath());
    }

    public DatabaseManager(Path dbPath) {
        this.dbPath = Objects.requireNonNull(dbPath);
    }

    /**
     * Opens or creates the database and ensures the schema is up to date.
     */
    public void initialize() throws SQLException {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (java.io.IOException e) {
            throw new SQLException("Cannot create database directory: " + e.getMessage(), e);
        }

        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        connection = DriverManager.getConnection(url);

        // Enable WAL mode for better concurrent read performance
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }

        createSchema();
    }

    /**
     * Returns the active database connection.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Closes the database connection.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // Best effort close
            }
        }
    }

    private void createSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS accounts (
                    id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    bank TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    primary_currency TEXT NOT NULL
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id TEXT PRIMARY KEY,
                    account_id TEXT NOT NULL,
                    date TEXT NOT NULL,
                    description TEXT NOT NULL,
                    amount_cents INTEGER NOT NULL,
                    currency TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    bank TEXT NOT NULL,
                    transaction_type TEXT,
                    category TEXT,
                    tags TEXT,
                    is_internal_transfer INTEGER NOT NULL DEFAULT 0,
                    issues TEXT,
                    source_file_hash TEXT NOT NULL,
                    FOREIGN KEY (account_id) REFERENCES accounts(id)
                )
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(date)
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_transactions_account ON transactions(account_id)
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    name TEXT PRIMARY KEY,
                    is_custom INTEGER NOT NULL DEFAULT 0
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS category_rules (
                    merchant_key TEXT PRIMARY KEY,
                    category_name TEXT NOT NULL,
                    FOREIGN KEY (category_name) REFERENCES categories(name)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS exchange_rates (
                    from_currency TEXT NOT NULL,
                    to_currency TEXT NOT NULL,
                    rate TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (from_currency, to_currency)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS budgets (
                    id TEXT PRIMARY KEY,
                    category_name TEXT NOT NULL,
                    limit_amount_cents INTEGER NOT NULL,
                    limit_currency TEXT NOT NULL,
                    period_type TEXT NOT NULL,
                    period_start TEXT,
                    period_end TEXT
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS imported_files (
                    hash TEXT PRIMARY KEY,
                    filename TEXT NOT NULL,
                    imported_at TEXT NOT NULL
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS gmail_credentials (
                    email TEXT PRIMARY KEY,
                    encrypted_password TEXT NOT NULL
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """);
        }
    }

    private static Path defaultDbPath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            appData = System.getProperty("user.home");
        }
        return Path.of(appData, APP_DIR, DB_FILENAME);
    }
}
