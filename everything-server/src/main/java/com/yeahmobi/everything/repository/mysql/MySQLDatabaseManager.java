package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.common.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the MySQL database connection and initialization.
 * <p>
 * Reads MySQL connection configuration from {@link Config}:
 * <ul>
 *   <li>{@code mysql.url} — JDBC connection URL</li>
 *   <li>{@code mysql.username} — database username</li>
 *   <li>{@code mysql.password} — database password</li>
 * </ul>
 * Provides connection management and initializes tables by executing
 * {@code sql/init-mysql.sql} from the classpath.
 * </p>
 */
public class MySQLDatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(MySQLDatabaseManager.class);
    
    private static final String INIT_SQL_RESOURCE = "sql/init-mysql.sql";
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/yeahmobi_everything";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "";

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private Connection connection;

    /**
     * Creates a MySQLDatabaseManager using configuration from Config.
     * Falls back to default values if not configured.
     *
     * @param config the application configuration
     */
    public MySQLDatabaseManager(Config config) {
        String configuredUrl = config.getMysqlUrl();
        this.jdbcUrl = (configuredUrl != null && !configuredUrl.isBlank())
                ? configuredUrl : DEFAULT_URL;

        String configuredUsername = config.getMysqlUsername();
        this.username = (configuredUsername != null && !configuredUsername.isBlank())
                ? configuredUsername : DEFAULT_USERNAME;

        String configuredPassword = config.getMysqlPassword();
        this.password = (configuredPassword != null) ? configuredPassword : DEFAULT_PASSWORD;
    }

    /**
     * Creates a MySQLDatabaseManager with explicit connection parameters.
     * Useful for testing or custom configurations.
     *
     * @param jdbcUrl  the JDBC connection URL
     * @param username the database username
     * @param password the database password
     */
    public MySQLDatabaseManager(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Initializes the database: opens a connection and creates tables
     * by executing the init SQL script from the classpath.
     *
     * @throws SQLException if a database access error occurs
     * @throws IOException  if the init SQL resource cannot be read
     */
    public void initialize() throws SQLException, IOException {
        // Explicitly load MySQL JDBC driver (required for Fat JAR)
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            log.info("MySQL JDBC driver loaded successfully");
        } catch (ClassNotFoundException e) {
            log.error("MySQL JDBC driver not found in classpath", e);
            throw new SQLException("MySQL JDBC driver not found", e);
        }
        
        connection = DriverManager.getConnection(jdbcUrl, username, password);
        String sql = loadInitSql();
        String[] statements = parseSqlStatements(sql);
        List<String> schemaStatements = new ArrayList<>();
        List<String> seedStatements = new ArrayList<>();
        for (String statement : statements) {
            String normalized = statement.trim().toUpperCase();
            if (normalized.startsWith("INSERT") && normalized.contains("INTO SKILL")) {
                seedStatements.add(statement);
            } else {
                schemaStatements.add(statement);
            }
        }
        executeStatements(schemaStatements);
        ensureUserSchema();
        ensureSkillSchema();
        ensureSkillPackageSchema();
        executeStatements(seedStatements);
    }

    /**
     * Returns the current database connection.
     * If the connection is closed or null, a new connection is created.
     *
     * @return a valid MySQL connection
     * @throws SQLException if a database access error occurs
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl, username, password);
        }
        return connection;
    }

    /**
     * Closes the database connection and releases resources.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // Ignore close errors
            }
            connection = null;
        }
    }

    /**
     * Alias for {@link #close()} — shuts down the database manager.
     */
    public void shutdown() {
        close();
    }

    /**
     * Returns the configured JDBC URL.
     *
     * @return the JDBC URL string
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Returns the configured database username.
     *
     * @return the database username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the configured database password.
     *
     * @return the database password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the classpath resource path for the init SQL file.
     *
     * @return the init SQL resource path
     */
    public static String getInitSqlResource() {
        return INIT_SQL_RESOURCE;
    }

    /**
     * Reads the init SQL file from the classpath and returns its content.
     * This method is exposed for testing purposes (to verify SQL loading).
     *
     * @return the SQL content as a string
     * @throws IOException if the resource is not found or cannot be read
     */
    public String loadInitSql() throws IOException {
        return readResource(INIT_SQL_RESOURCE);
    }

    /**
     * Parses a SQL script into individual statements by splitting on semicolons.
     * Filters out empty statements and comments-only blocks.
     *
     * @param sql the full SQL script content
     * @return an array of individual SQL statements
     */
    public static String[] parseSqlStatements(String sql) {
        if (sql == null || sql.isBlank()) {
            return new String[0];
        }
        String[] rawStatements = sql.split(";");
        return java.util.Arrays.stream(rawStatements)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> !isCommentOnly(s))
                .toArray(String[]::new);
    }

    // ---- Internal helpers ----

    private void executeStatements(List<String> statements) throws SQLException {
        if (statements == null || statements.isEmpty()) {
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            for (String statement : statements) {
                stmt.execute(statement);
            }
        }
    }

    /**
     * Ensures user table has required columns for new releases.
     */
    private void ensureUserSchema() throws SQLException {
        if (!columnExists("user", "is_admin")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE user ADD COLUMN is_admin BOOLEAN DEFAULT FALSE");
                stmt.execute("CREATE INDEX idx_user_admin ON user(is_admin)");
            }
        }
    }

    private void ensureSkillSchema() throws SQLException {
        if (!columnExists("skill", "execution_mode")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE skill ADD COLUMN execution_mode ENUM('SINGLE', 'MULTI') DEFAULT 'SINGLE'");
                stmt.execute("CREATE INDEX idx_skill_execution_mode ON skill(execution_mode)");
            }
        }

        if (!columnExists("skill", "examples_json")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE skill ADD COLUMN examples_json TEXT");
            }
        }

        if (!columnExists("skill", "i18n_json")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE skill ADD COLUMN i18n_json LONGTEXT");
            }
        }

        if (!columnExists("skill", "source")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE skill ADD COLUMN source VARCHAR(50) DEFAULT 'admin'");
            }
        }

        if (!columnExists("skill", "source_lang")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE skill ADD COLUMN source_lang VARCHAR(20)");
            }
        }

        if (!columnExists("skill", "quality_tier")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE skill ADD COLUMN quality_tier ENUM('basic', 'verified') DEFAULT 'basic'");
                stmt.execute("CREATE INDEX idx_skill_quality_tier ON skill(quality_tier)");
            }
        }

        if (!columnExists("skill", "tool_ids_json")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE skill ADD COLUMN tool_ids_json TEXT");
            }
        }

        if (!columnExists("skill", "tool_groups_json")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE skill ADD COLUMN tool_groups_json TEXT");
            }
        }

        if (!columnExists("skill", "context_policy")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE skill ADD COLUMN context_policy VARCHAR(50) DEFAULT 'default'");
                stmt.execute("CREATE INDEX idx_skill_context_policy ON skill(context_policy)");
            }
        }
    }

    private void ensureSkillPackageSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS skill_package (
                        id VARCHAR(36) PRIMARY KEY,
                        skill_name VARCHAR(100) NOT NULL,
                        skill_version VARCHAR(64) NOT NULL,
                        artifact_sha256 CHAR(64) NOT NULL,
                        source_url VARCHAR(1024),
                        source_type ENUM('upload', 'download') DEFAULT 'upload',
                        status ENUM('INSTALLED', 'FAILED') DEFAULT 'INSTALLED',
                        active BOOLEAN DEFAULT TRUE,
                        install_message TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_skill_name_version (skill_name, skill_version),
                        INDEX idx_skill_package_name (skill_name),
                        INDEX idx_skill_package_sha (artifact_sha256),
                        INDEX idx_skill_package_active (active)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS skill_package_audit (
                        id VARCHAR(36) PRIMARY KEY,
                        package_id VARCHAR(36),
                        skill_name VARCHAR(100),
                        skill_version VARCHAR(64),
                        artifact_sha256 CHAR(64),
                        action VARCHAR(64) NOT NULL,
                        actor VARCHAR(100),
                        detail TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_skill_package_audit_name (skill_name),
                        INDEX idx_skill_package_audit_action (action),
                        INDEX idx_skill_package_audit_time (created_at),
                        CONSTRAINT fk_skill_package_audit_package
                            FOREIGN KEY (package_id) REFERENCES skill_package(id) ON DELETE SET NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Reads a classpath resource as a UTF-8 string.
     *
     * @param resourcePath the classpath resource path
     * @return the resource content as a string
     * @throws IOException if the resource is not found or cannot be read
     */
    private String readResource(String resourcePath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * Checks if a SQL string contains only comments (lines starting with --).
     *
     * @param sql the SQL string to check
     * @return true if the string contains only comments or whitespace
     */
    private static boolean isCommentOnly(String sql) {
        for (String line : sql.split("\n")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("--")) {
                return false;
            }
        }
        return true;
    }
}
