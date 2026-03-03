package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.common.Config;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MySQLDatabaseManager}.
 * <p>
 * Since no real MySQL server is available in the test environment,
 * these tests verify:
 * <ul>
 *   <li>Configuration reading (URL, username, password from Config)</li>
 *   <li>SQL file loading from classpath</li>
 *   <li>Connection URL construction</li>
 *   <li>SQL statement parsing logic</li>
 * </ul>
 * </p>
 */
class MySQLDatabaseManagerTest {

    // ---- Configuration reading tests ----

    @Test
    void constructorWithConfigReadsAllProperties() {
        Properties props = new Properties();
        props.setProperty("mysql.url", "jdbc:mysql://db-host:3307/mydb");
        props.setProperty("mysql.username", "admin");
        props.setProperty("mysql.password", "secret123");
        Config config = Config.fromProperties(props);

        MySQLDatabaseManager manager = new MySQLDatabaseManager(config);

        assertEquals("jdbc:mysql://db-host:3307/mydb", manager.getJdbcUrl());
        assertEquals("admin", manager.getUsername());
        assertEquals("secret123", manager.getPassword());
    }

    @Test
    void constructorWithConfigUsesDefaultsWhenNotConfigured() {
        Config config = Config.fromProperties(new Properties());

        MySQLDatabaseManager manager = new MySQLDatabaseManager(config);

        assertEquals("jdbc:mysql://localhost:3306/yeahmobi_everything", manager.getJdbcUrl());
        assertEquals("root", manager.getUsername());
        assertEquals("", manager.getPassword());
    }

    @Test
    void constructorWithConfigUsesDefaultForBlankUrl() {
        Properties props = new Properties();
        props.setProperty("mysql.url", "   ");
        props.setProperty("mysql.username", "  ");
        Config config = Config.fromProperties(props);

        MySQLDatabaseManager manager = new MySQLDatabaseManager(config);

        assertEquals("jdbc:mysql://localhost:3306/yeahmobi_everything", manager.getJdbcUrl());
        assertEquals("root", manager.getUsername());
    }

    @Test
    void constructorWithConfigHandlesNullPassword() {
        Properties props = new Properties();
        // password not set at all -> null from Config
        Config config = Config.fromProperties(props);

        MySQLDatabaseManager manager = new MySQLDatabaseManager(config);

        assertEquals("", manager.getPassword());
    }

    @Test
    void constructorWithExplicitParametersSetsCorrectValues() {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://custom-host:3308/testdb", "testuser", "testpass");

        assertEquals("jdbc:mysql://custom-host:3308/testdb", manager.getJdbcUrl());
        assertEquals("testuser", manager.getUsername());
        assertEquals("testpass", manager.getPassword());
    }

    @Test
    void constructorWithConfigPreservesUrlParameters() {
        Properties props = new Properties();
        props.setProperty("mysql.url",
                "jdbc:mysql://localhost:3306/yeahmobi_everything?useSSL=false&characterEncoding=utf8mb4");
        Config config = Config.fromProperties(props);

        MySQLDatabaseManager manager = new MySQLDatabaseManager(config);

        assertTrue(manager.getJdbcUrl().contains("useSSL=false"));
        assertTrue(manager.getJdbcUrl().contains("characterEncoding=utf8mb4"));
    }

    // ---- SQL file loading tests ----

    @Test
    void loadInitSqlReturnsNonEmptyContent() throws IOException {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://localhost:3306/test", "root", "");

        String sql = manager.loadInitSql();

        assertNotNull(sql);
        assertFalse(sql.isBlank(), "Init SQL should not be empty");
    }

    @Test
    void loadInitSqlContainsAllRequiredTables() throws IOException {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://localhost:3306/test", "root", "");

        String sql = manager.loadInitSql();

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS user"),
                "SQL should contain user table creation");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS skill"),
                "SQL should contain skill table creation");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS knowledge_file"),
                "SQL should contain knowledge_file table creation");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS skill_knowledge_binding"),
                "SQL should contain skill_knowledge_binding table creation");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS feedback"),
                "SQL should contain feedback table creation");
    }

    @Test
    void loadInitSqlContainsRequiredIndexes() throws IOException {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://localhost:3306/test", "root", "");

        String sql = manager.loadInitSql();

        // User indexes
        assertTrue(sql.contains("idx_user_email"), "SQL should contain user email index");
        assertTrue(sql.contains("idx_user_feishu"), "SQL should contain user feishu index");

        // Skill indexes
        assertTrue(sql.contains("idx_skill_category"), "SQL should contain skill category index");
        assertTrue(sql.contains("idx_skill_type"), "SQL should contain skill type index");
        assertTrue(sql.contains("idx_skill_kind"), "SQL should contain skill kind index");

        // Binding indexes
        assertTrue(sql.contains("idx_binding_skill"), "SQL should contain binding skill index");
        assertTrue(sql.contains("idx_binding_file"), "SQL should contain binding file index");

        // Feedback indexes
        assertTrue(sql.contains("idx_feedback_user"), "SQL should contain feedback user index");
        assertTrue(sql.contains("idx_feedback_status"), "SQL should contain feedback status index");
        assertTrue(sql.contains("idx_feedback_time"), "SQL should contain feedback time index");
    }

    @Test
    void loadInitSqlContainsForeignKeys() throws IOException {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://localhost:3306/test", "root", "");

        String sql = manager.loadInitSql();

        // skill_knowledge_binding foreign keys
        assertTrue(sql.contains("FOREIGN KEY (skill_id) REFERENCES skill(id)"),
                "SQL should contain skill_id foreign key");
        assertTrue(sql.contains("FOREIGN KEY (knowledge_file_id) REFERENCES knowledge_file(id)"),
                "SQL should contain knowledge_file_id foreign key");

        // feedback foreign key
        assertTrue(sql.contains("FOREIGN KEY (user_id) REFERENCES user(id)"),
                "SQL should contain user_id foreign key in feedback");
    }

    @Test
    void loadInitSqlContainsEnumTypes() throws IOException {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://localhost:3306/test", "root", "");

        String sql = manager.loadInitSql();

        // User login_type enum
        assertTrue(sql.contains("ENUM('email', 'feishu')"),
                "SQL should contain login_type enum");

        // Skill enums
        assertTrue(sql.contains("ENUM('GENERAL', 'INTERNAL')"),
                "SQL should contain skill_type enum");
        assertTrue(sql.contains("ENUM('PROMPT_ONLY', 'KNOWLEDGE_RAG')"),
                "SQL should contain skill_kind enum");

        // Knowledge file source_type enum
        assertTrue(sql.contains("ENUM('upload', 'manual')"),
                "SQL should contain source_type enum");

        // Feedback status enum
        assertTrue(sql.contains("ENUM('pending', 'processed')"),
                "SQL should contain feedback status enum");
    }

    @Test
    void initSqlResourcePathIsCorrect() {
        assertEquals("sql/init-mysql.sql", MySQLDatabaseManager.getInitSqlResource());
    }

    // ---- SQL parsing tests ----

    @Test
    void parseSqlStatementsHandlesMultipleStatements() {
        String sql = "CREATE TABLE a (id INT);\nCREATE TABLE b (id INT);";

        String[] statements = MySQLDatabaseManager.parseSqlStatements(sql);

        assertEquals(2, statements.length);
        assertTrue(statements[0].contains("CREATE TABLE a"));
        assertTrue(statements[1].contains("CREATE TABLE b"));
    }

    @Test
    void parseSqlStatementsFiltersEmptyStatements() {
        String sql = "CREATE TABLE a (id INT);;;\n\n;CREATE TABLE b (id INT);";

        String[] statements = MySQLDatabaseManager.parseSqlStatements(sql);

        assertEquals(2, statements.length);
    }

    @Test
    void parseSqlStatementsFiltersCommentOnlyBlocks() {
        String sql = "-- This is a comment\n-- Another comment;\nCREATE TABLE a (id INT);";

        String[] statements = MySQLDatabaseManager.parseSqlStatements(sql);

        assertEquals(1, statements.length);
        assertTrue(statements[0].contains("CREATE TABLE a"));
    }

    @Test
    void parseSqlStatementsHandlesNullInput() {
        String[] statements = MySQLDatabaseManager.parseSqlStatements(null);
        assertEquals(0, statements.length);
    }

    @Test
    void parseSqlStatementsHandlesBlankInput() {
        String[] statements = MySQLDatabaseManager.parseSqlStatements("   ");
        assertEquals(0, statements.length);
    }

    @Test
    void parseSqlStatementsPreservesStatementContent() {
        String sql = "CREATE TABLE user (\n    id VARCHAR(36) PRIMARY KEY,\n    email VARCHAR(255)\n);";

        String[] statements = MySQLDatabaseManager.parseSqlStatements(sql);

        assertEquals(1, statements.length);
        assertTrue(statements[0].contains("VARCHAR(36)"));
        assertTrue(statements[0].contains("VARCHAR(255)"));
    }

    @Test
    void parseSqlStatementsFromInitFile() throws IOException {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://localhost:3306/test", "root", "");

        String sql = manager.loadInitSql();
        String[] statements = MySQLDatabaseManager.parseSqlStatements(sql);

        // Current init-mysql.sql uses inline INDEX definitions inside CREATE TABLE,
        // so statement count is expected to be roughly: 5 CREATE TABLE + 1 seed INSERT = 6.
        assertTrue(statements.length >= 6,
                "Init SQL should contain at least 6 statements (tables + seed), got: "
                        + statements.length);
    }

    // ---- Close/shutdown tests ----

    @Test
    void closeIsIdempotent() {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://localhost:3306/test", "root", "");

        // Close without ever connecting should not throw
        assertDoesNotThrow(manager::close);
        assertDoesNotThrow(manager::close);
    }

    @Test
    void shutdownIsIdempotent() {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://localhost:3306/test", "root", "");

        assertDoesNotThrow(manager::shutdown);
        assertDoesNotThrow(manager::shutdown);
    }
}
