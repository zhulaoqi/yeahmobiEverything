package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.common.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocalDatabaseManager}.
 */
class LocalDatabaseManagerTest {

    private LocalDatabaseManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
    }

    @Test
    void initializeCreatesAllTables() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        Set<String> tables = getTableNames(manager.getConnection());

        assertTrue(tables.contains("local_session"), "local_session table should exist");
        assertTrue(tables.contains("chat_session"), "chat_session table should exist");
        assertTrue(tables.contains("chat_message"), "chat_message table should exist");
        assertTrue(tables.contains("favorite"), "favorite table should exist");
        assertTrue(tables.contains("skill_usage"), "skill_usage table should exist");
        assertTrue(tables.contains("settings"), "settings table should exist");
    }

    @Test
    void initializeEnablesForeignKeys() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        try (Statement stmt = manager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Foreign keys should be enabled");
        }
    }

    @Test
    void initializeEnablesWalMode() throws SQLException, IOException {
        // WAL mode only works with file-based databases, not :memory:
        Path tempDb = Files.createTempFile("test-local-db", ".db");
        try {
            manager = new LocalDatabaseManager(tempDb.toString());
            manager.initialize();

            try (Statement stmt = manager.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1).toLowerCase(),
                        "Journal mode should be WAL");
            }
        } finally {
            manager.close();
            Files.deleteIfExists(tempDb);
            // WAL mode creates additional files
            Files.deleteIfExists(Path.of(tempDb + "-wal"));
            Files.deleteIfExists(Path.of(tempDb + "-shm"));
        }
    }

    @Test
    void getConnectionReturnsValidConnection() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        Connection conn = manager.getConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
    }

    @Test
    void getConnectionReconnectsAfterClose() throws SQLException, IOException {
        Path tempDb = Files.createTempFile("test-reconnect-db", ".db");
        try {
            manager = new LocalDatabaseManager(tempDb.toString());
            manager.initialize();

            Connection first = manager.getConnection();
            assertFalse(first.isClosed());

            // Close the connection
            first.close();

            // getConnection should create a new one
            Connection second = manager.getConnection();
            assertNotNull(second);
            assertFalse(second.isClosed());
        } finally {
            manager.close();
            Files.deleteIfExists(tempDb);
            Files.deleteIfExists(Path.of(tempDb + "-wal"));
            Files.deleteIfExists(Path.of(tempDb + "-shm"));
        }
    }

    @Test
    void closeReleasesConnection() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        Connection conn = manager.getConnection();
        assertFalse(conn.isClosed());

        manager.close();
        assertTrue(conn.isClosed());
    }

    @Test
    void shutdownReleasesConnection() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        Connection conn = manager.getConnection();
        assertFalse(conn.isClosed());

        manager.shutdown();
        assertTrue(conn.isClosed());
    }

    @Test
    void constructorWithConfigUsesConfiguredPath() {
        Properties props = new Properties();
        props.setProperty("db.local.path", "/tmp/test-config.db");
        Config config = Config.fromProperties(props);

        manager = new LocalDatabaseManager(config);
        assertEquals("/tmp/test-config.db", manager.getDbPath());
        assertEquals("jdbc:sqlite:/tmp/test-config.db", manager.getJdbcUrl());
    }

    @Test
    void constructorWithConfigUsesDefaultWhenNotConfigured() {
        Properties props = new Properties();
        Config config = Config.fromProperties(props);

        manager = new LocalDatabaseManager(config);
        String expectedDefault = System.getProperty("user.home")
                + "/.yeahmobi-everything/data.db";
        assertEquals(expectedDefault, manager.getDbPath());
    }

    @Test
    void constructorWithExplicitPathSetsCorrectJdbcUrl() {
        manager = new LocalDatabaseManager(":memory:");
        assertEquals(":memory:", manager.getDbPath());
        assertEquals("jdbc:sqlite::memory:", manager.getJdbcUrl());
    }

    @Test
    void initializeCreatesParentDirectories() throws SQLException, IOException {
        Path tempDir = Files.createTempDirectory("test-local-db-dir");
        Path dbPath = tempDir.resolve("subdir/nested/data.db");

        try {
            manager = new LocalDatabaseManager(dbPath.toString());
            manager.initialize();

            assertTrue(Files.exists(dbPath.getParent()),
                    "Parent directories should be created");

            // Verify the database is functional
            Set<String> tables = getTableNames(manager.getConnection());
            assertTrue(tables.contains("settings"), "Tables should be created");
        } finally {
            manager.close();
            // Clean up
            Files.deleteIfExists(dbPath);
            Files.deleteIfExists(Path.of(dbPath + "-wal"));
            Files.deleteIfExists(Path.of(dbPath + "-shm"));
            Files.deleteIfExists(dbPath.getParent());
            Files.deleteIfExists(dbPath.getParent().getParent());
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void initializeIsIdempotent() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        // Insert some data
        try (Statement stmt = manager.getConnection().createStatement()) {
            stmt.execute("INSERT INTO settings (key, value) VALUES ('theme', 'dark')");
        }

        // Re-initialize should not fail (CREATE TABLE IF NOT EXISTS)
        // Note: for in-memory DB, re-initialize on same connection is fine
        // The SQL uses IF NOT EXISTS so it won't error
        try (Statement stmt = manager.getConnection().createStatement()) {
            String sql = readInitSql();
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }

        // Data should still be there
        try (Statement stmt = manager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT value FROM settings WHERE key = 'theme'")) {
            assertTrue(rs.next());
            assertEquals("dark", rs.getString(1));
        }
    }

    @Test
    void chatMessageForeignKeyConstraint() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        // Insert a chat_session first
        try (Statement stmt = manager.getConnection().createStatement()) {
            stmt.execute("INSERT INTO chat_session (id, user_id, skill_id, skill_name, last_message, last_timestamp) "
                    + "VALUES ('s1', 'u1', 'sk1', 'Test Skill', 'hello', 1000)");
        }

        // Insert a chat_message referencing the session
        try (Statement stmt = manager.getConnection().createStatement()) {
            stmt.execute("INSERT INTO chat_message (id, session_id, skill_id, role, content, timestamp) "
                    + "VALUES ('m1', 's1', 'sk1', 'user', 'hello', 1000)");
        }

        // Inserting a message with non-existent session should fail
        try (Statement stmt = manager.getConnection().createStatement()) {
            assertThrows(SQLException.class, () ->
                    stmt.execute("INSERT INTO chat_message (id, session_id, skill_id, role, content, timestamp) "
                            + "VALUES ('m2', 'nonexistent', 'sk1', 'user', 'hello', 1000)"));
        }
    }

    @Test
    void chatMessageCascadeDelete() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        try (Statement stmt = manager.getConnection().createStatement()) {
            stmt.execute("INSERT INTO chat_session (id, user_id, skill_id, skill_name, last_message, last_timestamp) "
                    + "VALUES ('s1', 'u1', 'sk1', 'Test Skill', 'hello', 1000)");
            stmt.execute("INSERT INTO chat_message (id, session_id, skill_id, role, content, timestamp) "
                    + "VALUES ('m1', 's1', 'sk1', 'user', 'hello', 1000)");
            stmt.execute("INSERT INTO chat_message (id, session_id, skill_id, role, content, timestamp) "
                    + "VALUES ('m2', 's1', 'sk1', 'assistant', 'hi there', 1001)");
        }

        // Delete the session — messages should cascade
        try (Statement stmt = manager.getConnection().createStatement()) {
            stmt.execute("DELETE FROM chat_session WHERE id = 's1'");
        }

        try (Statement stmt = manager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM chat_message WHERE session_id = 's1'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "Messages should be cascade-deleted");
        }
    }

    @Test
    void roleCheckConstraint() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        // Insert a session first
        try (Statement stmt = manager.getConnection().createStatement()) {
            stmt.execute("INSERT INTO chat_session (id, user_id, skill_id, skill_name, last_message, last_timestamp) "
                    + "VALUES ('s1', 'u1', 'sk1', 'Test Skill', 'hello', 1000)");
        }

        // Invalid role should fail
        try (Statement stmt = manager.getConnection().createStatement()) {
            assertThrows(SQLException.class, () ->
                    stmt.execute("INSERT INTO chat_message (id, session_id, skill_id, role, content, timestamp) "
                            + "VALUES ('m1', 's1', 'sk1', 'invalid_role', 'hello', 1000)"));
        }
    }

    @Test
    void loginTypeCheckConstraint() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        // Valid login types should work
        try (Statement stmt = manager.getConnection().createStatement()) {
            stmt.execute("INSERT INTO local_session (token, user_id, username, email, login_type, expires_at, created_at) "
                    + "VALUES ('tok1', 'u1', 'user1', 'a@b.com', 'email', 9999999, 123456)");
            stmt.execute("INSERT INTO local_session (token, user_id, username, email, login_type, expires_at, created_at) "
                    + "VALUES ('tok2', 'u2', 'user2', 'c@d.com', 'feishu', 9999999, 123456)");
        }

        // Invalid login type should fail
        try (Statement stmt = manager.getConnection().createStatement()) {
            assertThrows(SQLException.class, () ->
                    stmt.execute("INSERT INTO local_session (token, user_id, username, email, login_type, expires_at, created_at) "
                            + "VALUES ('tok3', 'u3', 'user3', 'e@f.com', 'google', 9999999, 123456)"));
        }
    }

    @Test
    void closeIsIdempotent() throws SQLException, IOException {
        manager = new LocalDatabaseManager(":memory:");
        manager.initialize();

        manager.close();
        // Calling close again should not throw
        assertDoesNotThrow(() -> manager.close());
    }

    // ---- Helpers ----

    private Set<String> getTableNames(Connection conn) throws SQLException {
        Set<String> tables = new HashSet<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private String readInitSql() throws IOException {
        try (var is = getClass().getClassLoader().getResourceAsStream("sql/init-local.sql")) {
            if (is == null) throw new IOException("init-local.sql not found");
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
