package com.yeahmobi.everything.repository.local;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of {@link FavoriteRepository}.
 * <p>
 * Uses {@link LocalDatabaseManager} to obtain a connection to the local
 * SQLite database. Favorites are stored in the {@code favorite} table
 * with a composite primary key of (user_id, skill_id).
 * </p>
 */
public class FavoriteRepositoryImpl implements FavoriteRepository {

    private static final String INSERT_SQL =
            "INSERT OR IGNORE INTO favorite (user_id, skill_id, created_at) VALUES (?, ?, ?)";

    private static final String DELETE_SQL =
            "DELETE FROM favorite WHERE user_id = ? AND skill_id = ?";

    private static final String SELECT_ALL_SQL =
            "SELECT skill_id FROM favorite WHERE user_id = ? ORDER BY created_at DESC";

    private static final String SELECT_EXISTS_SQL =
            "SELECT COUNT(*) FROM favorite WHERE user_id = ? AND skill_id = ?";

    private final LocalDatabaseManager databaseManager;

    /**
     * Creates a FavoriteRepositoryImpl backed by the given database manager.
     *
     * @param databaseManager the local SQLite database manager
     */
    public FavoriteRepositoryImpl(LocalDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void addFavorite(String userId, String skillId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, userId);
                stmt.setString(2, skillId);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add favorite", e);
        }
    }

    @Override
    public void removeFavorite(String userId, String skillId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setString(1, userId);
                stmt.setString(2, skillId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove favorite", e);
        }
    }

    @Override
    public List<String> getFavoriteSkillIds(String userId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL)) {
                stmt.setString(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<String> skillIds = new ArrayList<>();
                    while (rs.next()) {
                        skillIds.add(rs.getString("skill_id"));
                    }
                    return skillIds;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get favorite skill IDs", e);
        }
    }

    @Override
    public boolean isFavorite(String userId, String skillId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_EXISTS_SQL)) {
                stmt.setString(1, userId);
                stmt.setString(2, skillId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check favorite status", e);
        }
    }
}
