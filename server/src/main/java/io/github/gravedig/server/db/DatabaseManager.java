package io.github.gravedig.server.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class DatabaseManager implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private final String jdbcUrl;
    private Connection keepAliveConnection;

    public DatabaseManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        init();
    }

    public static DatabaseManager createDefault() {
        String home = System.getProperty("user.home", ".");
        Path dir = Paths.get(home, ".gravedig");
        try {
            Files.createDirectories(dir);
            File dbFile = dir.resolve("gravedig.db").toFile();
            return new DatabaseManager("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Cannot access {} ({}); falling back to local working directory database", dir, e.getMessage());
            File localFile = new File("gravedig.db");
            return new DatabaseManager("jdbc:sqlite:" + localFile.getAbsolutePath());
        }
    }

    public static DatabaseManager createInMemory() {
        String id = UUID.randomUUID().toString().replace("-", "");
        return new DatabaseManager("jdbc:sqlite:file:memdb_" + id + "?mode=memory&cache=shared");
    }

    public static DatabaseManager createForFile(Path dbPath) {
        try {
            if (dbPath.getParent() != null) {
                Files.createDirectories(dbPath.getParent());
            }
        } catch (IOException e) {
            log.error("Failed to create directories for db: {}", dbPath, e);
        }
        return new DatabaseManager("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    private void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            if (jdbcUrl.contains("mode=memory") || jdbcUrl.contains(":memory:")) {
                // Keep shared memory connection open so DB is not wiped when connections close
                this.keepAliveConnection = DriverManager.getConnection(jdbcUrl);
            }

            Connection conn = (keepAliveConnection != null) ? keepAliveConnection : DriverManager.getConnection(jdbcUrl);
            try (Statement stmt = conn.createStatement()) {
                if (!jdbcUrl.contains("mode=memory") && !jdbcUrl.contains(":memory:")) {
                    stmt.execute("PRAGMA journal_mode=WAL;");
                }
                stmt.execute("PRAGMA foreign_keys=ON;");

                // Scans table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS scans (
                        id TEXT PRIMARY KEY,
                        target_path TEXT NOT NULL,
                        scan_mode TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        completed_at TEXT,
                        total_files INTEGER DEFAULT 0,
                        total_commits INTEGER DEFAULT 0,
                        total_findings INTEGER DEFAULT 0,
                        risk_score INTEGER DEFAULT 0,
                        error_message TEXT,
                        summary_json TEXT
                    );
                """);

                // Findings table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS findings (
                        id TEXT PRIMARY KEY,
                        scan_id TEXT NOT NULL,
                        rule_id TEXT NOT NULL,
                        rule_name TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        status TEXT NOT NULL,
                        presence TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        line_number INTEGER NOT NULL,
                        commit_hash TEXT,
                        author TEXT,
                        commit_date TEXT,
                        commit_message TEXT,
                        redacted_match TEXT,
                        entropy REAL,
                        validation_status TEXT,
                        raw_json TEXT NOT NULL,
                        FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
                    );
                """);

                // Indexes
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_findings_scan_id ON findings(scan_id);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_findings_severity ON findings(severity);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_findings_status ON findings(status);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_findings_presence ON findings(presence);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_scans_created_at ON scans(created_at DESC);");

                log.info("Initialized SQLite database schema at {}", jdbcUrl);
            } finally {
                if (keepAliveConnection == null && !conn.isClosed()) {
                    conn.close();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database: " + jdbcUrl, e);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public void close() {
        if (keepAliveConnection != null) {
            try {
                keepAliveConnection.close();
            } catch (SQLException e) {
                log.debug("Error closing keepalive connection", e);
            }
        }
    }
}
