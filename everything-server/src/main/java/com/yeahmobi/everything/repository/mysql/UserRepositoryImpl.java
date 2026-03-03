package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.auth.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * MySQL implementation of {@link UserRepository}.
 * <p>
 * Uses {@link MySQLDatabaseManager} to obtain a connection to the MySQL
 * database. All operations use {@link PreparedStatement} to prevent SQL
 * injection. The {@code created_at} column in MySQL is stored as a
 * TIMESTAMP but is converted to epoch millis in the {@link User} record.
 * </p>
 */
public class UserRepositoryImpl implements UserRepository {

    static final String INSERT_SQL =
            "INSERT INTO user (id, email, password_hash, username, feishu_user_id, login_type, is_admin, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, FROM_UNIXTIME(? / 1000))";

    static final String SELECT_BY_EMAIL_SQL =
            "SELECT id, email, password_hash, username, feishu_user_id, login_type, is_admin, "
                    + "UNIX_TIMESTAMP(created_at) * 1000 AS created_at_millis FROM user WHERE email = ?";

    static final String SELECT_BY_FEISHU_SQL =
            "SELECT id, email, password_hash, username, feishu_user_id, login_type, is_admin, "
                    + "UNIX_TIMESTAMP(created_at) * 1000 AS created_at_millis FROM user WHERE feishu_user_id = ?";

    static final String SELECT_BY_ID_SQL =
            "SELECT id, email, password_hash, username, feishu_user_id, login_type, is_admin, "
                    + "UNIX_TIMESTAMP(created_at) * 1000 AS created_at_millis FROM user WHERE id = ?";

    static final String PROMOTE_BY_ID_SQL =
            "UPDATE user SET is_admin = TRUE WHERE id = ?";

    static final String PROMOTE_BY_EMAIL_SQL =
            "UPDATE user SET is_admin = TRUE WHERE email = ?";

    private final MySQLDatabaseManager databaseManager;

    /**
     * Creates a UserRepositoryImpl backed by the given database manager.
     *
     * @param databaseManager the MySQL database manager
     */
    public UserRepositoryImpl(MySQLDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createUser(User user) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, user.id());
                stmt.setString(2, user.email());
                stmt.setString(3, user.passwordHash());
                stmt.setString(4, user.username());
                stmt.setString(5, user.feishuUserId());
                stmt.setString(6, user.loginType());
                stmt.setBoolean(7, user.isAdmin());
                stmt.setLong(8, user.createdAt());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create user", e);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return findByColumn(SELECT_BY_EMAIL_SQL, email);
    }

    @Override
    public Optional<User> findByFeishuUserId(String feishuUserId) {
        return findByColumn(SELECT_BY_FEISHU_SQL, feishuUserId);
    }

    @Override
    public Optional<User> findById(String userId) {
        return findByColumn(SELECT_BY_ID_SQL, userId);
    }

    @Override
    public void promoteToAdminById(String userId) {
        updateAdmin(PROMOTE_BY_ID_SQL, userId);
    }

    @Override
    public void promoteToAdminByEmail(String email) {
        updateAdmin(PROMOTE_BY_EMAIL_SQL, email);
    }

    /**
     * Returns the database manager used by this repository.
     * Exposed for testing purposes.
     *
     * @return the MySQL database manager
     */
    public MySQLDatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    // ---- Internal helpers ----

    private Optional<User> findByColumn(String sql, String value) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, value);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapUser(rs));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user", e);
        }
    }

    private void updateAdmin(String sql, String value) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, value);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update admin status", e);
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("username"),
                rs.getString("feishu_user_id"),
                rs.getString("login_type"),
                rs.getBoolean("is_admin"),
                rs.getLong("created_at_millis")
        );
    }
}
