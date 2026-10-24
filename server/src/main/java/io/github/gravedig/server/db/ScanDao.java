package io.github.gravedig.server.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.gravedig.core.model.ScanSummary;
import io.github.gravedig.server.model.ScanRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ScanDao {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private final DatabaseManager db;

    public ScanDao(DatabaseManager db) {
        this.db = db;
    }

    public void insert(ScanRecord record) {
        String sql = """
            INSERT INTO scans (id, target_path, scan_mode, status, created_at, total_files, total_commits, total_findings, risk_score)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, record.getId());
            stmt.setString(2, record.getTargetPath());
            stmt.setString(3, record.getScanMode());
            stmt.setString(4, record.getStatus());
            stmt.setString(5, record.getCreatedAt());
            stmt.setInt(6, record.getTotalFiles());
            stmt.setInt(7, record.getTotalCommits());
            stmt.setInt(8, record.getTotalFindings());
            stmt.setInt(9, record.getRiskScore());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert scan: " + record.getId(), e);
        }
    }

    public void updateProgress(String id, int files, int commits, int findings) {
        String sql = "UPDATE scans SET total_files = ?, total_commits = ?, total_findings = ? WHERE id = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, files);
            stmt.setInt(2, commits);
            stmt.setInt(3, findings);
            stmt.setString(4, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update scan progress: " + id, e);
        }
    }

    public void complete(String id, String completedAt, int files, int commits, int findings, int riskScore, ScanSummary summary) {
        String summaryJson = null;
        if (summary != null) {
            try {
                summaryJson = MAPPER.writeValueAsString(summary);
            } catch (Exception ignored) {
            }
        }
        String sql = """
            UPDATE scans
            SET status = 'COMPLETED', completed_at = ?, total_files = ?, total_commits = ?,
                total_findings = ?, risk_score = ?, summary_json = ?
            WHERE id = ?;
        """;
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, completedAt);
            stmt.setInt(2, files);
            stmt.setInt(3, commits);
            stmt.setInt(4, findings);
            stmt.setInt(5, riskScore);
            stmt.setString(6, summaryJson);
            stmt.setString(7, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to complete scan: " + id, e);
        }
    }

    public void fail(String id, String completedAt, String errorMessage) {
        String sql = "UPDATE scans SET status = 'FAILED', completed_at = ?, error_message = ? WHERE id = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, completedAt);
            stmt.setString(2, errorMessage);
            stmt.setString(3, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fail scan: " + id, e);
        }
    }

    public Optional<ScanRecord> findById(String id) {
        String sql = "SELECT * FROM scans WHERE id = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query scan: " + id, e);
        }
        return Optional.empty();
    }

    public List<ScanRecord> listRecent(int limit) {
        String sql = "SELECT * FROM scans ORDER BY created_at DESC LIMIT ?;";
        List<ScanRecord> results = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Math.max(limit, 1));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list recent scans", e);
        }
        return results;
    }

    public void delete(String id) {
        String sql = "DELETE FROM scans WHERE id = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete scan: " + id, e);
        }
    }

    public void deleteAll() {
        String sql = "DELETE FROM scans;";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all scans", e);
        }
    }

    private ScanRecord mapRow(ResultSet rs) throws SQLException {
        ScanRecord r = new ScanRecord();
        r.setId(rs.getString("id"));
        r.setTargetPath(rs.getString("target_path"));
        r.setScanMode(rs.getString("scan_mode"));
        r.setStatus(rs.getString("status"));
        r.setCreatedAt(rs.getString("created_at"));
        r.setCompletedAt(rs.getString("completed_at"));
        r.setTotalFiles(rs.getInt("total_files"));
        r.setTotalCommits(rs.getInt("total_commits"));
        r.setTotalFindings(rs.getInt("total_findings"));
        r.setRiskScore(rs.getInt("risk_score"));
        r.setErrorMessage(rs.getString("error_message"));

        String summaryJson = rs.getString("summary_json");
        if (summaryJson != null && !summaryJson.isBlank()) {
            try {
                r.setSummary(MAPPER.readValue(summaryJson, ScanSummary.class));
            } catch (Exception ignored) {
            }
        }
        return r;
    }
}
