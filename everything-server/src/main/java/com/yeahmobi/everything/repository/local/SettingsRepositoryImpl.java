package com.yeahmobi.everything.repository.local;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * SQLite implementation of {@link SettingsRepository}.
 * <p>
 * Uses {@link LocalDatabaseManager} to obtain a connection to the local
 * SQLite database. Settings are stored in the {@code settings} table as
 * key-value pairs. Upsert semantics are used so that saving a setting
 * with an existing key replaces the previous value.
 * </p>
 */
public class SettingsRepositoryImpl implements SettingsRepository {

    private static final String UPSERT_SQL =
            "INSERT INTO settings (key, value) VALUES (?, ?) "
                    + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";

    private static final String SELECT_SQL =
            "SELECT value FROM settings WHERE key = ?";

    private final LocalDatabaseManager databaseManager;

    /**
     * Creates a SettingsRepositoryImpl backed by the given database manager.
     *
     * @param databaseManager the local SQLite database manager
     */
    public SettingsRepositoryImpl(LocalDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void saveSetting(String key, String value) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, key);
                stmt.setString(2, value);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save setting: " + key, e);
        }
    }

    @Override
    public Optional<String> getSetting(String key) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_SQL)) {
                stmt.setString(1, key);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(rs.getString("value"));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get setting: " + key, e);
        }
    }
}
