package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SkillRepositoryImpl}.
 * <p>
 * Since we cannot connect to a real MySQL database in unit tests,
 * these tests verify the repository's configuration, SQL constants,
 * and constructor injection behavior.
 * </p>
 */
class SkillRepositoryImplTest {

    @Test
    void constructorAcceptsDatabaseManager() {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://localhost:3306/test", "testuser", "testpass");
        SkillRepositoryImpl repository = new SkillRepositoryImpl(manager);

        assertNotNull(repository);
        assertSame(manager, repository.getDatabaseManager());
    }

    @Test
    void insertSqlContainsAllColumns() {
        String sql = SkillRepositoryImpl.INSERT_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("id"), "INSERT SQL should reference id column");
        assertTrue(sql.contains("name"), "INSERT SQL should reference name column");
        assertTrue(sql.contains("description"), "INSERT SQL should reference description column");
        assertTrue(sql.contains("icon"), "INSERT SQL should reference icon column");
        assertTrue(sql.contains("category"), "INSERT SQL should reference category column");
        assertTrue(sql.contains("enabled"), "INSERT SQL should reference enabled column");
        assertTrue(sql.contains("skill_type"), "INSERT SQL should reference skill_type column");
        assertTrue(sql.contains("skill_kind"), "INSERT SQL should reference skill_kind column");
        assertTrue(sql.contains("prompt_template"), "INSERT SQL should reference prompt_template column");
        assertTrue(sql.contains("execution_mode"), "INSERT SQL should reference execution_mode column");
        assertTrue(sql.contains("created_at"), "INSERT SQL should reference created_at column");
    }

    @Test
    void selectByIdSqlIsValid() {
        String sql = SkillRepositoryImpl.SELECT_BY_ID_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("WHERE id = ?"), "Should query by id parameter");
        assertTrue(sql.contains("FROM skill"), "Should query from skill table");
    }

    @Test
    void selectAllSqlIsValid() {
        String sql = SkillRepositoryImpl.SELECT_ALL_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("FROM skill"), "Should query from skill table");
        assertTrue(sql.contains("ORDER BY created_at DESC"), "Should order by created_at descending");
    }

    @Test
    void updateSqlContainsAllUpdatableColumns() {
        String sql = SkillRepositoryImpl.UPDATE_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("UPDATE skill"), "Should update skill table");
        assertTrue(sql.contains("name = ?"), "Should update name");
        assertTrue(sql.contains("description = ?"), "Should update description");
        assertTrue(sql.contains("icon = ?"), "Should update icon");
        assertTrue(sql.contains("category = ?"), "Should update category");
        assertTrue(sql.contains("enabled = ?"), "Should update enabled");
        assertTrue(sql.contains("skill_type = ?"), "Should update skill_type");
        assertTrue(sql.contains("skill_kind = ?"), "Should update skill_kind");
        assertTrue(sql.contains("prompt_template = ?"), "Should update prompt_template");
        assertTrue(sql.contains("execution_mode = ?"), "Should update execution_mode");
        assertTrue(sql.contains("WHERE id = ?"), "Should target specific skill by id");
    }

    @Test
    void deleteSqlIsValid() {
        String sql = SkillRepositoryImpl.DELETE_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("DELETE FROM skill"), "Should delete from skill table");
        assertTrue(sql.contains("WHERE id = ?"), "Should target specific skill by id");
    }

    @Test
    void sqlStatementsUseParameterizedQueries() {
        assertTrue(SkillRepositoryImpl.INSERT_SQL.contains("?"),
                "INSERT SQL should use parameterized queries");
        assertTrue(SkillRepositoryImpl.SELECT_BY_ID_SQL.contains("?"),
                "SELECT by id SQL should use parameterized queries");
        assertTrue(SkillRepositoryImpl.UPDATE_SQL.contains("?"),
                "UPDATE SQL should use parameterized queries");
        assertTrue(SkillRepositoryImpl.DELETE_SQL.contains("?"),
                "DELETE SQL should use parameterized queries");
    }

    @Test
    void insertSqlConvertsEpochMillisToTimestamp() {
        String sql = SkillRepositoryImpl.INSERT_SQL;
        assertTrue(sql.contains("FROM_UNIXTIME"),
                "INSERT SQL should use FROM_UNIXTIME to convert epoch millis");
    }

    @Test
    void selectSqlConvertsTimestampToEpochMillis() {
        String sql = SkillRepositoryImpl.SELECT_BY_ID_SQL;
        assertTrue(sql.contains("UNIX_TIMESTAMP"),
                "SELECT SQL should use UNIX_TIMESTAMP to convert back to epoch millis");
        assertTrue(sql.contains("created_at_millis"),
                "SELECT SQL should alias the converted timestamp");
    }

    @Test
    void skillAdminRecordFieldsAreAccessible() {
        long now = System.currentTimeMillis();
        SkillAdmin skill = new SkillAdmin(
                "skill-001",
                "翻译助手",
                "支持多语言互译",
                "translate.png",
                "翻译",
                true,
                "输入文本并指定目标语言。",
                List.of("把这句话翻译成英文：你好", "Translate to Chinese: Hello"),
                "{\"zh-CN\":{\"displayName\":\"翻译助手\"}}",
                "admin",
                "zh",
                "verified",
                SkillType.GENERAL,
                SkillKind.PROMPT_ONLY,
                "你是一个翻译助手",
                SkillExecutionMode.SINGLE,
                now
        );

        assertEquals("skill-001", skill.id());
        assertEquals("翻译助手", skill.name());
        assertEquals("支持多语言互译", skill.description());
        assertEquals("translate.png", skill.icon());
        assertEquals("翻译", skill.category());
        assertTrue(skill.enabled());
        assertEquals("输入文本并指定目标语言。", skill.usageGuide());
        assertEquals(2, skill.examples().size());
        assertEquals("admin", skill.source());
        assertEquals("zh", skill.sourceLang());
        assertEquals("verified", skill.qualityTier());
        assertEquals(SkillType.GENERAL, skill.type());
        assertEquals(SkillKind.PROMPT_ONLY, skill.kind());
        assertEquals("你是一个翻译助手", skill.promptTemplate());
        assertEquals(SkillExecutionMode.SINGLE, skill.executionMode());
        assertEquals(now, skill.createdAt());
    }

    @Test
    void skillAdminRecordSupportsInternalType() {
        SkillAdmin skill = new SkillAdmin(
                "skill-002",
                "内部工具",
                "公司内部专用",
                "internal.png",
                "内部",
                true,
                "",
                List.of(),
                null,
                "admin",
                "zh",
                "basic",
                SkillType.INTERNAL,
                SkillKind.KNOWLEDGE_RAG,
                "你是一个内部知识助手",
                SkillExecutionMode.MULTI,
                System.currentTimeMillis()
        );

        assertEquals(SkillType.INTERNAL, skill.type());
        assertEquals(SkillKind.KNOWLEDGE_RAG, skill.kind());
        assertEquals(SkillExecutionMode.MULTI, skill.executionMode());
    }

    @Test
    void skillAdminRecordSupportsDisabledState() {
        SkillAdmin skill = new SkillAdmin(
                "skill-003",
                "已禁用",
                "已禁用的 Skill",
                null,
                "其他",
                false,
                null,
                List.of(),
                null,
                "admin",
                "zh",
                "basic",
                SkillType.GENERAL,
                SkillKind.PROMPT_ONLY,
                null,
                SkillExecutionMode.SINGLE,
                System.currentTimeMillis()
        );

        assertFalse(skill.enabled());
        assertNull(skill.icon());
        assertNull(skill.promptTemplate());
    }
}
