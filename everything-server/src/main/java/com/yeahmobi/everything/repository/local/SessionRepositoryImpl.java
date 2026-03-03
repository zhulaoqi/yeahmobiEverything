package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.auth.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * SQLite implementation of {@link SessionRepository}.
 * <p>
 * Uses {@link LocalDatabaseManager} to obtain a connection to the local
 * SQLite database. Sessions are stored in the {@code local_session} table.
 * Only one session is kept at a time — saving a new session clears any
 * existing sessions first.
 * </p>
 */
public class SessionRepositoryImpl implements SessionRepository {

    private static final String INSERT_SQL =
            "INSERT INTO local_session (token, user_id, username, email, login_type, expires_at, created_at, is_admin) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_SQL =
            "SELECT token, user_id, username, email, login_type, expires_at, created_at, is_admin "
                    + "FROM local_session ORDER BY id DESC LIMIT 1";

    private static final String DELETE_ALL_SQL = "DELETE FROM local_session";

    private final LocalDatabaseManager databaseManager;

    /**
     * Creates a SessionRepositoryImpl backed by the given database manager.
     *
     * @param databaseManager the local SQLite database manager
     */
    public SessionRepositoryImpl(LocalDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void saveSession(Session session) {
        try {
            Connection conn = databaseManager.getConnection();
            // Clear existing sessions first
            try (PreparedStatement deleteStmt = conn.prepareStatement(DELETE_ALL_SQL)) {
                deleteStmt.executeUpdate();
            }
            // Insert the new session
            try (PreparedStatement insertStmt = conn.prepareStatement(INSERT_SQL)) {
                insertStmt.setString(1, session.token());
                insertStmt.setString(2, session.userId());
                insertStmt.setString(3, session.username());
                insertStmt.setString(4, session.email());
                insertStmt.setString(5, session.loginType());
                insertStmt.setLong(6, session.expiresAt());
                insertStmt.setLong(7, session.createdAt());
                insertStmt.setInt(8, session.isAdmin() ? 1 : 0);
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save session", e);
        }
    }

    @Override
    public Optional<Session> loadSession() {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_SQL);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Session session = new Session(
                            rs.getString("token"),
                            rs.getString("user_id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("login_type"),
                            rs.getLong("expires_at"),
                            rs.getLong("created_at"),
                            rs.getInt("is_admin") == 1
                    );
                    return Optional.of(session);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load session", e);
        }
    }

    @Override
    public void clearSession() {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_ALL_SQL)) {
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear session", e);
        }
    }
}
