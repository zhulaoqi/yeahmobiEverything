package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.knowledge.KnowledgeFile;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL implementation of {@link KnowledgeFileRepository}.
 */
public class KnowledgeFileRepositoryImpl implements KnowledgeFileRepository {

    private final MySQLDatabaseManager dbManager;

    public KnowledgeFileRepositoryImpl(MySQLDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void saveFile(KnowledgeFile file) {
        String sql = "INSERT INTO knowledge_file (id, file_name, file_type, file_size, file_path, source_type, extracted_text, uploaded_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, FROM_UNIXTIME(? / 1000), FROM_UNIXTIME(? / 1000))";
        try {
            Connection conn = dbManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, file.id());
                stmt.setString(2, file.fileName());
                stmt.setString(3, file.fileType());
                stmt.setLong(4, file.fileSize());
                stmt.setString(5, file.fileName()); // file_path = fileName for simplicity
                stmt.setString(6, file.sourceType());
                stmt.setString(7, file.extractedText());
                stmt.setLong(8, file.uploadedAt());
                stmt.setLong(9, file.updatedAt());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save knowledge file: " + file.id(), e);
        }
    }

    @Override
    public Optional<KnowledgeFile> getFile(String fileId) {
        String sql = "SELECT id, file_name, file_type, file_size, source_type, extracted_text, "
                + "UNIX_TIMESTAMP(uploaded_at) * 1000 AS uploaded_at_millis, "
                + "UNIX_TIMESTAMP(updated_at) * 1000 AS updated_at_millis FROM knowledge_file WHERE id = ?";
        try {
            Connection conn = dbManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, fileId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get knowledge file: " + fileId, e);
        }
    }

    @Override
    public List<KnowledgeFile> getAllFiles() {
        String sql = "SELECT id, file_name, file_type, file_size, source_type, extracted_text, "
                + "UNIX_TIMESTAMP(uploaded_at) * 1000 AS uploaded_at_millis, "
                + "UNIX_TIMESTAMP(updated_at) * 1000 AS updated_at_millis FROM knowledge_file ORDER BY uploaded_at DESC";
        try {
            Connection conn = dbManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                List<KnowledgeFile> files = new ArrayList<>();
                while (rs.next()) {
                    files.add(mapRow(rs));
                }
                return files;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get all knowledge files", e);
        }
    }

    @Override
    public void updateFile(KnowledgeFile file) {
        String sql = "UPDATE knowledge_file SET file_name = ?, file_type = ?, file_size = ?, "
                + "source_type = ?, extracted_text = ?, updated_at = FROM_UNIXTIME(? / 1000) WHERE id = ?";
        try {
            Connection conn = dbManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, file.fileName());
                stmt.setString(2, file.fileType());
                stmt.setLong(3, file.fileSize());
                stmt.setString(4, file.sourceType());
                stmt.setString(5, file.extractedText());
                stmt.setLong(6, file.updatedAt());
                stmt.setString(7, file.id());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update knowledge file: " + file.id(), e);
        }
    }

    @Override
    public void deleteFile(String fileId) {
        String sql = "DELETE FROM knowledge_file WHERE id = ?";
        try {
            Connection conn = dbManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, fileId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete knowledge file: " + fileId, e);
        }
    }

    private KnowledgeFile mapRow(ResultSet rs) throws SQLException {
        return new KnowledgeFile(
                rs.getString("id"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                rs.getLong("file_size"),
                rs.getString("source_type"),
                rs.getString("extracted_text"),
                rs.getLong("uploaded_at_millis"),
                rs.getLong("updated_at_millis")
        );
    }
}
