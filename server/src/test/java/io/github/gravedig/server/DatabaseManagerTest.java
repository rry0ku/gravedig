package io.github.gravedig.server;

import io.github.gravedig.server.db.DatabaseManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseManagerTest {

    @Test
    @DisplayName("Initializes SQLite database and creates required tables")
    void testInitTables() throws Exception {
        try (DatabaseManager db = DatabaseManager.createInMemory()) {
            try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table';")) {
                    boolean hasScans = false;
                    boolean hasFindings = false;
                    while (rs.next()) {
                        String name = rs.getString("name");
                        if ("scans".equalsIgnoreCase(name)) hasScans = true;
                        if ("findings".equalsIgnoreCase(name)) hasFindings = true;
                    }
                    assertThat(hasScans).isTrue();
                    assertThat(hasFindings).isTrue();
                }
            }
        }
    }
}
