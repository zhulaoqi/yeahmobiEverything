package com.yeahmobi.everything.repository.mysql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Repository for skill ZIP package install records.
 */
public class SkillPackageRepository {

    private final MySQLDatabaseManager db;

    public SkillPackageRepository(MySQLDatabaseManager db) {
        this.db = db;
    }

    public Optional<SkillPackageRecord> findByNameAndVersion(String skillName, String skillVersion) {
        String sql = """
                SELECT id, skill_name, skill_version, artifact_sha256, source_url, source_type, status, active, install_message
                FROM skill_package
                WHERE skill_name = ? AND skill_version = ?
                """;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, skillName);
            stmt.setString(2, skillVersion);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public void deactivateOtherVersions(String skillName, String keepPackageId) throws SQLException {
        String sql = "UPDATE skill_package SET active = FALSE WHERE skill_name = ? AND id <> ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, skillName);
            stmt.setString(2, keepPackageId);
            stmt.executeUpdate();
        }
    }

    public void upsertInstalled(SkillPackageRecord record) throws SQLException {
        String sql = """
                INSERT INTO skill_package
                (id, skill_name, skill_version, artifact_sha256, source_url, source_type, status, active, install_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    artifact_sha256 = VALUES(artifact_sha256),
                    source_url = VALUES(source_url),
                    source_type = VALUES(source_type),
                    status = VALUES(status),
                    active = VALUES(active),
                    install_message = VALUES(install_message)
                """;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, record.id());
            stmt.setString(2, record.skillName());
            stmt.setString(3, record.skillVersion());
            stmt.setString(4, record.artifactSha256());
            stmt.setString(5, record.sourceUrl());
            stmt.setString(6, record.sourceType());
            stmt.setString(7, record.status());
            stmt.setBoolean(8, record.active());
            stmt.setString(9, record.installMessage());
            stmt.executeUpdate();
        }
    }

    public void insertAudit(SkillPackageAuditRecord audit) throws SQLException {
        String sql = """
                INSERT INTO skill_package_audit
                (id, package_id, skill_name, skill_version, artifact_sha256, action, actor, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, audit.id());
            stmt.setString(2, audit.packageId());
            stmt.setString(3, audit.skillName());
            stmt.setString(4, audit.skillVersion());
            stmt.setString(5, audit.artifactSha256());
            stmt.setString(6, audit.action());
            stmt.setString(7, audit.actor());
            stmt.setString(8, audit.detail());
            stmt.executeUpdate();
        }
    }

    private SkillPackageRecord map(ResultSet rs) throws SQLException {
        return new SkillPackageRecord(
                rs.getString("id"),
                rs.getString("skill_name"),
                rs.getString("skill_version"),
                rs.getString("artifact_sha256"),
                rs.getString("source_url"),
                rs.getString("source_type"),
                rs.getString("status"),
                rs.getBoolean("active"),
                rs.getString("install_message")
        );
    }

    public record SkillPackageRecord(
            String id,
            String skillName,
            String skillVersion,
            String artifactSha256,
            String sourceUrl,
            String sourceType,
            String status,
            boolean active,
            String installMessage
    ) {}

    public record SkillPackageAuditRecord(
            String id,
            String packageId,
            String skillName,
            String skillVersion,
            String artifactSha256,
            String action,
            String actor,
            String detail
    ) {}
}

