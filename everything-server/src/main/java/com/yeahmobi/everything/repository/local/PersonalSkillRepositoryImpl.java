package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.personalskill.PersonalSkill;
import com.yeahmobi.everything.personalskill.PersonalSkillRepository;
import com.yeahmobi.everything.personalskill.PersonalSkillStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of {@link PersonalSkillRepository}.
 */
public class PersonalSkillRepositoryImpl implements PersonalSkillRepository {

    private static final String INSERT_SQL =
            "INSERT INTO personal_skill (id, user_id, name, description, category, prompt_template, status, reviewer_note, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL =
            "UPDATE personal_skill SET name = ?, description = ?, category = ?, prompt_template = ?, "
                    + "status = ?, reviewer_note = ?, updated_at = ? WHERE id = ?";

    private static final String SELECT_BY_ID_SQL =
            "SELECT * FROM personal_skill WHERE id = ?";

    private static final String SELECT_BY_USER_SQL =
            "SELECT * FROM personal_skill WHERE user_id = ? ORDER BY updated_at DESC";

    private static final String SELECT_PENDING_SQL =
            "SELECT * FROM personal_skill WHERE status = 'PENDING' ORDER BY created_at DESC";

    private static final String UPDATE_STATUS_SQL =
            "UPDATE personal_skill SET status = ?, reviewer_note = ?, updated_at = ? WHERE id = ?";

    private final LocalDatabaseManager databaseManager;

    public PersonalSkillRepositoryImpl(LocalDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void save(PersonalSkill skill) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, skill.id());
                stmt.setString(2, skill.userId());
                stmt.setString(3, skill.name());
                stmt.setString(4, skill.description());
                stmt.setString(5, skill.category());
                stmt.setString(6, skill.promptTemplate());
                stmt.setString(7, skill.status().name());
                stmt.setString(8, skill.reviewerNote());
                stmt.setLong(9, skill.createdAt());
                stmt.setLong(10, skill.updatedAt());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save personal skill", e);
        }
    }

    @Override
    public void update(PersonalSkill skill) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                stmt.setString(1, skill.name());
                stmt.setString(2, skill.description());
                stmt.setString(3, skill.category());
                stmt.setString(4, skill.promptTemplate());
                stmt.setString(5, skill.status().name());
                stmt.setString(6, skill.reviewerNote());
                stmt.setLong(7, skill.updatedAt());
                stmt.setString(8, skill.id());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update personal skill", e);
        }
    }

    @Override
    public Optional<PersonalSkill> getById(String id) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get personal skill", e);
        }
    }

    @Override
    public List<PersonalSkill> getByUser(String userId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_BY_USER_SQL)) {
                stmt.setString(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PersonalSkill> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                    return list;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get personal skills by user", e);
        }
    }

    @Override
    public List<PersonalSkill> getPending() {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_PENDING_SQL)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PersonalSkill> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                    return list;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get pending personal skills", e);
        }
    }

    @Override
    public void updateStatus(String id, PersonalSkillStatus status, String reviewerNote) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_STATUS_SQL)) {
                stmt.setString(1, status.name());
                stmt.setString(2, reviewerNote);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setString(4, id);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update personal skill status", e);
        }
    }

    private PersonalSkill mapRow(ResultSet rs) throws SQLException {
        return new PersonalSkill(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("category"),
                rs.getString("prompt_template"),
                PersonalSkillStatus.valueOf(rs.getString("status")),
                rs.getString("reviewer_note"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
