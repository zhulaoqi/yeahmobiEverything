package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.feedback.Feedback;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL implementation of {@link FeedbackRepository}.
 * Stores and retrieves feedback records from the MySQL feedback table.
 */
public class FeedbackRepositoryImpl implements FeedbackRepository {

    private final MySQLDatabaseManager dbManager;

    public FeedbackRepositoryImpl(MySQLDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void saveFeedback(Feedback feedback) {
        String sql = "INSERT INTO feedback (id, user_id, username, content, status, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, feedback.id());
            stmt.setString(2, feedback.userId());
            stmt.setString(3, feedback.username());
            stmt.setString(4, feedback.content());
            stmt.setString(5, feedback.status());
            stmt.setTimestamp(6, new Timestamp(feedback.timestamp()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save feedback: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Feedback> getAllFeedbacks() {
        String sql = "SELECT id, user_id, username, content, created_at, status FROM feedback ORDER BY created_at DESC";
        List<Feedback> feedbacks = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                feedbacks.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve feedbacks: " + e.getMessage(), e);
        }
        return feedbacks;
    }

    @Override
    public Optional<Feedback> getFeedback(String feedbackId) {
        String sql = "SELECT id, user_id, username, content, created_at, status FROM feedback WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, feedbackId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve feedback: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public void updateFeedbackStatus(String feedbackId, String status, long processedAt) {
        String sql = "UPDATE feedback SET status = ?, processed_at = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setTimestamp(2, new Timestamp(processedAt));
            stmt.setString(3, feedbackId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update feedback status: " + e.getMessage(), e);
        }
    }

    /**
     * Maps a ResultSet row to a Feedback record.
     */
    private Feedback mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String userId = rs.getString("user_id");
        String username = rs.getString("username");
        String content = rs.getString("content");
        Timestamp createdAt = rs.getTimestamp("created_at");
        long timestamp = createdAt != null ? createdAt.getTime() : 0L;
        String status = rs.getString("status");
        return new Feedback(id, userId, username, content, timestamp, status);
    }
}
