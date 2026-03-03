package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.common.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Manages the local SQLite database connection and initialization.
 * <p>
 * Reads the database path from {@link Config} (key: {@code db.local.path}),
 * defaulting to {@code ~/.yeahmobi-everything/data.db}. Creates parent
 * directories if they don't exist, enables WAL mode and foreign keys,
 * and initializes tables by executing {@code sql/init-local.sql} from
 * the classpath.
 * </p>
 * <p>
 * For testing, pass {@code :memory:} or a custom path to the constructor
 * that accepts an explicit database path.
 * </p>
 */
public class LocalDatabaseManager {

    private static final String INIT_SQL_RESOURCE = "sql/init-local.sql";
    private static final String DEFAULT_DB_PATH = System.getProperty("user.home")
            + "/.yeahmobi-everything/data.db";

    private final String dbPath;
    private final String jdbcUrl;
    private Connection connection;

    /**
     * Creates a LocalDatabaseManager using the path from Config.
     * Falls back to {@code ~/.yeahmobi-everything/data.db} if not configured.
     *
     * @param config the application configuration
     */
    public LocalDatabaseManager(Config config) {
        String configuredPath = config.getLocalDbPath();
        this.dbPath = (configuredPath != null && !configuredPath.isBlank())
                ? configuredPath : DEFAULT_DB_PATH;
        this.jdbcUrl = "jdbc:sqlite:" + this.dbPath;
    }

    /**
     * Creates a LocalDatabaseManager with an explicit database path.
     * Use {@code ":memory:"} for an in-memory database (useful for testing).
     *
     * @param dbPath the SQLite database file path, or ":memory:" for in-memory
     */
    public LocalDatabaseManager(String dbPath) {
        this.dbPath = dbPath;
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
    }

    /**
     * Initializes the database: creates parent directories (for file-based DBs),
     * opens a connection, enables WAL mode and foreign keys, and creates tables.
     *
     * @throws SQLException if a database access error occurs
     * @throws IOException  if the init SQL resource cannot be read
     */
    public void initialize() throws SQLException, IOException {
        ensureParentDirectories();
        connection = DriverManager.getConnection(jdbcUrl);
        enablePragmas();
        executeSqlFromResource(INIT_SQL_RESOURCE);
        ensureLocalSessionSchema();
    }

    /**
     * Returns the current database connection.
     * If the connection is closed or null, a new connection is created
     * with WAL mode and foreign keys enabled.
     *
     * @return a valid SQLite connection
     * @throws SQLException if a database access error occurs
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
            enablePragmas();
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
     * Returns the configured database path.
     *
     * @return the database file path or ":memory:"
     */
    public String getDbPath() {
        return dbPath;
    }

    /**
     * Returns the JDBC URL used for connections.
     *
     * @return the JDBC URL string
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    // ---- Internal helpers ----

    /**
     * Creates parent directories for the database file if they don't exist.
     * Skipped for in-memory databases.
     */
    private void ensureParentDirectories() throws IOException {
        if (":memory:".equals(dbPath)) {
            return;
        }
        Path parentDir = Paths.get(dbPath).getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
    }

    /**
     * Enables WAL journal mode and foreign key enforcement.
     */
    private void enablePragmas() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
    }

    /**
     * Reads a SQL file from the classpath and executes all statements.
     * Statements are split by semicolons.
     *
     * @param resourcePath the classpath resource path
     * @throws SQLException if a SQL execution error occurs
     * @throws IOException  if the resource cannot be read
     */
    private void executeSqlFromResource(String resourcePath) throws SQLException, IOException {
        String sql = readResource(resourcePath);
        try (Statement stmt = connection.createStatement()) {
            // Split by semicolons and execute each non-empty statement
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    /**
     * Ensures local_session has required columns for new releases.
     */
    private void ensureLocalSessionSchema() throws SQLException {
        if (!columnExists("local_session", "created_at")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE local_session ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0");
            }
        }
        if (!columnExists("local_session", "is_admin")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE local_session ADD COLUMN is_admin INTEGER NOT NULL DEFAULT 0");
            }
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (columnName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
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
}
