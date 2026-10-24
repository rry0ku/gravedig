package io.github.gravedig.server.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.gravedig.core.model.Finding;
import io.github.gravedig.core.model.FindingPresence;
import io.github.gravedig.core.model.FindingStatus;
import io.github.gravedig.server.model.FindingFilter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FindingDao {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private final DatabaseManager db;

    public FindingDao(DatabaseManager db) {
        this.db = db;
    }

    public void insertBatch(String scanId, List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return;
        }

        String sql = """
            INSERT OR REPLACE INTO findings (
                id, scan_id, rule_id, rule_name, severity, status, presence, file_path, line_number,
                commit_hash, author, commit_date, commit_message, redacted_match, entropy,
                validation_status, raw_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = db.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Finding f : findings) {
                    stmt.setString(1, f.getId());
                    stmt.setString(2, scanId);
                    stmt.setString(3, f.getRuleId());
                    stmt.setString(4, f.getRuleName());
                    stmt.setString(5, f.getSeverity().name());
                    stmt.setString(6, f.getStatus() != null ? f.getStatus().name() : FindingStatus.OPEN.name());
                    stmt.setString(7, f.getPresence() != null ? f.getPresence().name() : FindingPresence.ALIVE.name());
                    stmt.setString(8, f.getFilePath());
                    stmt.setInt(9, f.getLineNumber());
                    stmt.setString(10, f.getCommitHash());
                    stmt.setString(11, f.getCommitAuthor());
                    stmt.setString(12, f.getCommitDate() != null ? f.getCommitDate().toString() : null);
                    stmt.setString(13, f.getDescription());
                    stmt.setString(14, f.getSecretRedacted());
                    stmt.setDouble(15, f.getEntropy());
                    stmt.setString(16, f.getValidationReason());
                    stmt.setString(17, MAPPER.writeValueAsString(f));
                    stmt.addBatch();
                }
                stmt.executeBatch();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to batch insert findings for scan: " + scanId, e);
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error batch inserting findings", e);
        }
    }

    public List<Finding> findByScanId(String scanId, FindingFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT raw_json, status, presence FROM findings WHERE scan_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(scanId);

        if (filter != null) {
            if (filter.getSeverity() != null && !filter.getSeverity().isBlank()) {
                sql.append(" AND severity = ?");
                params.add(filter.getSeverity().toUpperCase());
            }
            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                sql.append(" AND status = ?");
                params.add(filter.getStatus().toUpperCase());
            }
            if (filter.getPresence() != null && !filter.getPresence().isBlank()) {
                sql.append(" AND presence = ?");
                params.add(filter.getPresence().toUpperCase());
            }
            if (filter.getRuleId() != null && !filter.getRuleId().isBlank()) {
                sql.append(" AND (rule_id LIKE ? OR rule_name LIKE ?)");
                params.add("%" + filter.getRuleId() + "%");
                params.add("%" + filter.getRuleId() + "%");
            }
            if (filter.getFilePath() != null && !filter.getFilePath().isBlank()) {
                sql.append(" AND file_path LIKE ?");
                params.add("%" + filter.getFilePath() + "%");
            }

            sql.append(" ORDER BY line_number ASC LIMIT ? OFFSET ?");
            params.add(filter.getLimit() > 0 ? filter.getLimit() : 500);
            params.add(Math.max(0, filter.getOffset()));
        } else {
            sql.append(" ORDER BY line_number ASC LIMIT 500");
        }

        List<Finding> list = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String raw = rs.getString("raw_json");
                    String statusCol = rs.getString("status");
                    String presenceCol = rs.getString("presence");
                    Finding finding = MAPPER.readValue(raw, Finding.class);
                    if (statusCol != null) {
                        try {
                            finding.setStatus(FindingStatus.valueOf(statusCol));
                        } catch (Exception ignored) {
                        }
                    }
                    if (presenceCol != null) {
                        try {
                            finding.setPresence(FindingPresence.valueOf(presenceCol));
                        } catch (Exception ignored) {
                        }
                    }
                    list.add(finding);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query findings for scan: " + scanId, e);
        }
        return list;
    }

    public Optional<Finding> findById(String id) {
        String sql = "SELECT raw_json, status, presence FROM findings WHERE id = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String raw = rs.getString("raw_json");
                    String statusCol = rs.getString("status");
                    String presenceCol = rs.getString("presence");
                    Finding finding = MAPPER.readValue(raw, Finding.class);
                    if (statusCol != null) {
                        try {
                            finding.setStatus(FindingStatus.valueOf(statusCol));
                        } catch (Exception ignored) {
                        }
                    }
                    if (presenceCol != null) {
                        try {
                            finding.setPresence(FindingPresence.valueOf(presenceCol));
                        } catch (Exception ignored) {
                        }
                    }
                    return Optional.of(finding);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find finding by id: " + id, e);
        }
        return Optional.empty();
    }

    public boolean updateStatus(String id, FindingStatus status) {
        Optional<Finding> existingOpt = findById(id);
        if (existingOpt.isEmpty()) {
            return false;
        }

        Finding finding = existingOpt.get();
        finding.setStatus(status);

        String sql = "UPDATE findings SET status = ?, raw_json = ? WHERE id = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setString(2, MAPPER.writeValueAsString(finding));
            stmt.setString(3, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update status for finding: " + id, e);
        }
    }
}
