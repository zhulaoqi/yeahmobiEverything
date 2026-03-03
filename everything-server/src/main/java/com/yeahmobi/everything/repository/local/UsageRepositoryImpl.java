package com.yeahmobi.everything.repository.local;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of {@link UsageRepository}.
 * <p>
 * Uses {@link LocalDatabaseManager} to obtain a connection to the local
 * SQLite database. Usage records are stored in the {@code skill_usage} table.
 * </p>
 */
public class UsageRepositoryImpl implements UsageRepository {

    private static final String INSERT_SQL =
            "INSERT INTO skill_usage (user_id, skill_id, used_at) VALUES (?, ?, ?)";

    /**
     * Selects distinct skill IDs for a user, ordered by the most recent usage time.
     * Groups by skill_id and takes the MAX(used_at) for ordering, then limits the result.
     */
    private static final String SELECT_RECENT_SQL =
            "SELECT skill_id FROM skill_usage WHERE user_id = ? "
                    + "GROUP BY skill_id ORDER BY MAX(used_at) DESC LIMIT ?";

    private static final String SELECT_COUNT_SQL =
            "SELECT COUNT(*) FROM skill_usage WHERE user_id = ? AND skill_id = ?";

    private final LocalDatabaseManager databaseManager;

    /**
     * Creates a UsageRepositoryImpl backed by the given database manager.
     *
     * @param databaseManager the local SQLite database manager
     */
    public UsageRepositoryImpl(LocalDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void recordUsage(String userId, String skillId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, userId);
                stmt.setString(2, skillId);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record usage", e);
        }
    }

    @Override
    public List<String> getRecentSkillIds(String userId, int limit) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_RECENT_SQL)) {
                stmt.setString(1, userId);
                stmt.setInt(2, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<String> skillIds = new ArrayList<>();
                    while (rs.next()) {
                        skillIds.add(rs.getString("skill_id"));
                    }
                    return skillIds;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get recent skill IDs", e);
        }
    }

    @Override
    public int getUsageCount(String userId, String skillId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_COUNT_SQL)) {
                stmt.setString(1, userId);
                stmt.setString(2, skillId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get usage count", e);
        }
    }
}
