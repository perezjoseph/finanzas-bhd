package com.pfa.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pfa.core.Bank;
import com.pfa.core.Currency;
import com.pfa.core.Direction;
import com.pfa.core.FieldIssue;
import com.pfa.core.Money;
import com.pfa.core.Transaction;

/**
 * Data access object for Transaction entities.
 * Stores amounts as integer cents to avoid float precision issues.
 */
public class TransactionDao {

    private final Connection connection;

    public TransactionDao(Connection connection) {
        this.connection = connection;
    }

    public void insert(Transaction tx) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO transactions
            (id, account_id, date, description, amount_cents, currency, direction, bank,
             transaction_type, category, tags, is_internal_transfer, issues, source_file_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setTransactionParams(ps, tx);
            ps.executeUpdate();
        }
    }

    public void insertBatch(List<Transaction> transactions) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO transactions
            (id, account_id, date, description, amount_cents, currency, direction, bank,
             transaction_type, category, tags, is_internal_transfer, issues, source_file_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Transaction tx : transactions) {
                setTransactionParams(ps, tx);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static final String ALL_COLUMNS = """
            id, account_id, date, description, amount_cents, currency, direction, bank,
            transaction_type, category, tags, is_internal_transfer, issues, source_file_hash""";

    private static final String SELECT_ALL = "SELECT " + ALL_COLUMNS + " FROM transactions";

    public List<Transaction> findAll() throws SQLException {
        String sql = SELECT_ALL + " ORDER BY date DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapResults(rs);
        }
    }

    public List<Transaction> findByAccountId(String accountId) throws SQLException {
        String sql = SELECT_ALL + " WHERE account_id = ? ORDER BY date DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapResults(rs);
            }
        }
    }

    public Optional<Transaction> findById(UUID id) throws SQLException {
        String sql = SELECT_ALL + " WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> results = mapResults(rs);
                return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
            }
        }
    }

    public void deleteById(UUID id) throws SQLException {
        String sql = "DELETE FROM transactions WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    public void deleteBySourceHash(String hash) throws SQLException {
        String sql = "DELETE FROM transactions WHERE source_file_hash = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.executeUpdate();
        }
    }

    public void updateInternalTransfer(UUID id, boolean isInternalTransfer) throws SQLException {
        String sql = "UPDATE transactions SET is_internal_transfer = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, isInternalTransfer ? 1 : 0);
            ps.setString(2, id.toString());
            ps.executeUpdate();
        }
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM transactions";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void setTransactionParams(PreparedStatement ps, Transaction tx) throws SQLException {
        ps.setString(1, tx.id().toString());
        ps.setString(2, tx.accountId());
        ps.setString(3, tx.date().toString());
        ps.setString(4, tx.description());
        ps.setLong(5, tx.amount().amount().movePointRight(2).longValueExact());
        ps.setString(6, tx.amount().currency().name());
        ps.setString(7, tx.direction().name());
        ps.setString(8, tx.bank().name());
        ps.setString(9, tx.transactionType().orElse(null));
        ps.setString(10, tx.category().orElse(null));
        ps.setString(11, String.join(",", tx.tags()));
        ps.setInt(12, tx.isInternalTransfer() ? 1 : 0);
        ps.setString(13, tx.issues().stream().map(Enum::name).collect(Collectors.joining(",")));
        ps.setString(14, tx.sourceFileHash());
    }

    private List<Transaction> mapResults(ResultSet rs) throws SQLException {
        List<Transaction> results = new ArrayList<>();
        while (rs.next()) {
            results.add(mapRow(rs));
        }
        return results;
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        Currency currency = Currency.valueOf(rs.getString("currency"));
        long cents = rs.getLong("amount_cents");
        BigDecimal amount = BigDecimal.valueOf(cents, 2);

        String tagsStr = rs.getString("tags");
        List<String> tags = (tagsStr == null || tagsStr.isEmpty())
                ? List.of()
                : Arrays.asList(tagsStr.split(","));

        String issuesStr = rs.getString("issues");
        Set<FieldIssue> issues = (issuesStr == null || issuesStr.isEmpty())
                ? Set.of()
                : Arrays.stream(issuesStr.split(","))
                        .map(FieldIssue::valueOf)
                        .collect(Collectors.toSet());

        return new Transaction(
                UUID.fromString(rs.getString("id")),
                rs.getString("account_id"),
                LocalDate.parse(rs.getString("date")),
                rs.getString("description"),
                new Money(amount, currency),
                Direction.valueOf(rs.getString("direction")),
                Bank.valueOf(rs.getString("bank")),
                Optional.ofNullable(rs.getString("transaction_type")),
                Optional.ofNullable(rs.getString("category")),
                tags,
                rs.getInt("is_internal_transfer") == 1,
                issues,
                rs.getString("source_file_hash")
        );
    }
}
