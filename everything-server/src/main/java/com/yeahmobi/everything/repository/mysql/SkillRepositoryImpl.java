package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL implementation of {@link SkillRepository}.
 * <p>
 * Uses {@link MySQLDatabaseManager} to obtain a connection to the MySQL
 * database. All operations use {@link PreparedStatement} to prevent SQL
 * injection. The {@code created_at} column in MySQL is stored as a
 * TIMESTAMP but is converted to epoch millis in the {@link SkillAdmin} record.
 * </p>
 */
public class SkillRepositoryImpl implements SkillRepository {

    private static final Gson GSON = new Gson();
    private static final Type LIST_STRING_TYPE = new TypeToken<List<String>>() {}.getType();

    static final String INSERT_SQL =
            "INSERT INTO skill (id, name, description, icon, category, enabled, usage_guide, examples_json, i18n_json, "
                    + "source, source_lang, quality_tier, tool_ids_json, tool_groups_json, context_policy, "
                    + "skill_type, skill_kind, prompt_template, execution_mode, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FROM_UNIXTIME(? / 1000))";

    static final String SELECT_BY_ID_SQL =
            "SELECT id, name, description, icon, category, enabled, usage_guide, examples_json, i18n_json, source, source_lang, "
                    + "quality_tier, tool_ids_json, tool_groups_json, context_policy, "
                    + "skill_type, skill_kind, prompt_template, execution_mode, "
                    + "UNIX_TIMESTAMP(created_at) * 1000 AS created_at_millis FROM skill WHERE id = ?";

    static final String SELECT_ALL_SQL =
            "SELECT id, name, description, icon, category, enabled, usage_guide, examples_json, i18n_json, source, source_lang, "
                    + "quality_tier, tool_ids_json, tool_groups_json, context_policy, "
                    + "skill_type, skill_kind, prompt_template, execution_mode, "
                    + "UNIX_TIMESTAMP(created_at) * 1000 AS created_at_millis FROM skill ORDER BY created_at DESC";

    static final String UPDATE_SQL =
            "UPDATE skill SET name = ?, description = ?, icon = ?, category = ?, enabled = ?, usage_guide = ?, "
                    + "examples_json = ?, i18n_json = ?, source = ?, source_lang = ?, quality_tier = ?, "
                    + "tool_ids_json = ?, tool_groups_json = ?, context_policy = ?, "
                    + "skill_type = ?, skill_kind = ?, prompt_template = ?, execution_mode = ? WHERE id = ?";

    static final String DELETE_SQL = "DELETE FROM skill WHERE id = ?";

    private final MySQLDatabaseManager databaseManager;

    /**
     * Creates a SkillRepositoryImpl backed by the given database manager.
     *
     * @param databaseManager the MySQL database manager
     */
    public SkillRepositoryImpl(MySQLDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void saveSkill(SkillAdmin skill) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, skill.id());
                stmt.setString(2, skill.name());
                stmt.setString(3, skill.description());
                stmt.setString(4, skill.icon());
                stmt.setString(5, skill.category());
                stmt.setBoolean(6, skill.enabled());
                stmt.setString(7, normalizeNullable(skill.usageGuide()));
                stmt.setString(8, examplesToJson(skill.examples()));
                stmt.setString(9, normalizeNullable(skill.i18nJson()));
                stmt.setString(10, normalizeNullable(skill.source()));
                stmt.setString(11, normalizeNullable(skill.sourceLang()));
                stmt.setString(12, normalizeNullable(skill.qualityTier()));
                stmt.setString(13, listToJson(skill.toolIds()));
                stmt.setString(14, listToJson(skill.toolGroups()));
                stmt.setString(15, normalizeNullable(skill.contextPolicy()));
                stmt.setString(16, skill.type().name());
                stmt.setString(17, skill.kind().name());
                stmt.setString(18, skill.promptTemplate());
                stmt.setString(19, skill.executionMode().name());
                stmt.setLong(20, skill.createdAt());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save skill: " + skill.id(), e);
        }
    }

    @Override
    public Optional<SkillAdmin> getSkill(String skillId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, skillId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapSkillAdmin(rs));
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get skill: " + skillId, e);
        }
    }

    @Override
    public List<SkillAdmin> getAllSkills() {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL);
                 ResultSet rs = stmt.executeQuery()) {
                List<SkillAdmin> skills = new ArrayList<>();
                while (rs.next()) {
                    skills.add(mapSkillAdmin(rs));
                }
                return skills;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get all skills", e);
        }
    }

    @Override
    public void updateSkill(SkillAdmin skill) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                stmt.setString(1, skill.name());
                stmt.setString(2, skill.description());
                stmt.setString(3, skill.icon());
                stmt.setString(4, skill.category());
                stmt.setBoolean(5, skill.enabled());
                stmt.setString(6, normalizeNullable(skill.usageGuide()));
                stmt.setString(7, examplesToJson(skill.examples()));
                stmt.setString(8, normalizeNullable(skill.i18nJson()));
                stmt.setString(9, normalizeNullable(skill.source()));
                stmt.setString(10, normalizeNullable(skill.sourceLang()));
                stmt.setString(11, normalizeNullable(skill.qualityTier()));
                stmt.setString(12, listToJson(skill.toolIds()));
                stmt.setString(13, listToJson(skill.toolGroups()));
                stmt.setString(14, normalizeNullable(skill.contextPolicy()));
                stmt.setString(15, skill.type().name());
                stmt.setString(16, skill.kind().name());
                stmt.setString(17, skill.promptTemplate());
                stmt.setString(18, skill.executionMode().name());
                stmt.setString(19, skill.id());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update skill: " + skill.id(), e);
        }
    }

    @Override
    public void deleteSkill(String skillId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setString(1, skillId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete skill: " + skillId, e);
        }
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

    private SkillAdmin mapSkillAdmin(ResultSet rs) throws SQLException {
        return new SkillAdmin(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("icon"),
                rs.getString("category"),
                rs.getBoolean("enabled"),
                rs.getString("usage_guide"),
                jsonToExamples(rs.getString("examples_json")),
                rs.getString("i18n_json"),
                rs.getString("source"),
                rs.getString("source_lang"),
                rs.getString("quality_tier"),
                jsonToList(rs.getString("tool_ids_json")),
                jsonToList(rs.getString("tool_groups_json")),
                rs.getString("context_policy"),
                SkillType.valueOf(rs.getString("skill_type")),
                SkillKind.valueOf(rs.getString("skill_kind")),
                rs.getString("prompt_template"),
                parseExecutionMode(rs.getString("execution_mode")),
                rs.getLong("created_at_millis")
        );
    }

    private SkillExecutionMode parseExecutionMode(String value) {
        if (value == null || value.isBlank()) {
            return SkillExecutionMode.SINGLE;
        }
        try {
            return SkillExecutionMode.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return SkillExecutionMode.SINGLE;
        }
    }

    private static List<String> jsonToExamples(String json) {
        return jsonToList(json);
    }

    private static List<String> jsonToList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = GSON.fromJson(json, LIST_STRING_TYPE);
            if (parsed == null) {
                return List.of();
            }
            return parsed.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String examplesToJson(List<String> examples) {
        return listToJson(examples);
    }

    private static String listToJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> cleaned = values.stream()
                .filter(s -> s != null && !s.isBlank())
                .toList();
        if (cleaned.isEmpty()) {
            return null;
        }
        return GSON.toJson(cleaned);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
