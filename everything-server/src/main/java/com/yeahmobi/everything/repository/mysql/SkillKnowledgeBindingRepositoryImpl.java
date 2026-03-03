package com.yeahmobi.everything.repository.mysql;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL implementation of {@link SkillKnowledgeBindingRepository}.
 */
public class SkillKnowledgeBindingRepositoryImpl implements SkillKnowledgeBindingRepository {

    private final MySQLDatabaseManager dbManager;

    public SkillKnowledgeBindingRepositoryImpl(MySQLDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void bind(String skillId, String fileId) {
        String sql = "INSERT INTO skill_knowledge_binding (skill_id, knowledge_file_id, bound_at) VALUES (?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE bound_at = NOW()";
        try {
            Connection conn = dbManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, skillId);
                stmt.setString(2, fileId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to bind file " + fileId + " to skill " + skillId, e);
        }
    }

    @Override
    public void unbind(String skillId, String fileId) {
        String sql = "DELETE FROM skill_knowledge_binding WHERE skill_id = ? AND knowledge_file_id = ?";
        try {
            Connection conn = dbManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, skillId);
                stmt.setString(2, fileId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unbind file " + fileId + " from skill " + skillId, e);
        }
    }

    @Override
    public void unbindAllForFile(String fileId) {
        String sql = "DELETE FROM skill_knowledge_binding WHERE knowledge_file_id = ?";
        try {
            Connection conn = dbManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, fileId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unbind all for file " + fileId, e);
        }
    }

    @Override
    public List<String> getFileIdsForSkill(String skillId) {
        String sql = "SELECT knowledge_file_id FROM skill_knowledge_binding WHERE skill_id = ?";
        try {
            Connection conn = dbManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, skillId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<String> ids = new ArrayList<>();
                    while (rs.next()) {
                        ids.add(rs.getString("knowledge_file_id"));
                    }
                    return ids;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get file IDs for skill " + skillId, e);
        }
    }

    @Override
    public List<String> getSkillIdsForFile(String fileId) {
        String sql = "SELECT skill_id FROM skill_knowledge_binding WHERE knowledge_file_id = ?";
        try {
            Connection conn = dbManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, fileId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<String> ids = new ArrayList<>();
                    while (rs.next()) {
                        ids.add(rs.getString("skill_id"));
                    }
                    return ids;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get skill IDs for file " + fileId, e);
        }
    }
}
