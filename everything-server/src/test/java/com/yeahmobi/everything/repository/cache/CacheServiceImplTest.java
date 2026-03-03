package com.yeahmobi.everything.repository.cache;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yeahmobi.everything.auth.Session;
import com.yeahmobi.everything.skill.Skill;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CacheServiceImpl}.
 * Uses Mockito to mock RedisManager and Jedis since no Redis server is available in tests.
 */
class CacheServiceImplTest {

    private RedisManager redisManager;
    private Jedis jedis;
    private CacheServiceImpl cacheService;
    private Gson gson;

    @BeforeEach
    void setUp() {
        redisManager = mock(RedisManager.class);
        jedis = mock(Jedis.class);
        when(redisManager.getJedis()).thenReturn(jedis);
        gson = new Gson();
        cacheService = new CacheServiceImpl(redisManager, gson);
    }

    // ========== Session caching tests ==========

    @Test
    void cacheSession_shouldSetKeyWithCorrectPrefixAndTTL() {
        Session session = new Session("tok123", "user1", "Alice", "alice@test.com", "email", 9999999L, 1700000000000L, false);

        cacheService.cacheSession("tok123", session, 604800);

        String expectedKey = "session:tok123";
        String expectedJson = gson.toJson(session);
        verify(jedis).set(eq(expectedKey), eq(expectedJson), any(SetParams.class));
        verify(jedis).close();
    }

    @Test
    void getCachedSession_shouldReturnSessionWhenPresent() {
        Session session = new Session("tok123", "user1", "Alice", "alice@test.com", "email", 9999999L, 1700000000000L, false);
        String json = gson.toJson(session);
        when(jedis.get("session:tok123")).thenReturn(json);

        Optional<Session> result = cacheService.getCachedSession("tok123");

        assertTrue(result.isPresent());
        assertEquals(session, result.get());
    }

    @Test
    void getCachedSession_shouldReturnEmptyWhenNotPresent() {
        when(jedis.get("session:tok123")).thenReturn(null);

        Optional<Session> result = cacheService.getCachedSession("tok123");

        assertTrue(result.isEmpty());
    }

    @Test
    void getCachedSession_shouldReturnEmptyOnRedisFailure() {
        when(redisManager.getJedis()).thenThrow(new RuntimeException("Redis down"));

        Optional<Session> result = cacheService.getCachedSession("tok123");

        assertTrue(result.isEmpty());
    }

    @Test
    void removeCachedSession_shouldDeleteCorrectKey() {
        cacheService.removeCachedSession("tok123");

        verify(jedis).del("session:tok123");
        verify(jedis).close();
    }

    @Test
    void removeCachedSession_shouldHandleRedisFailureGracefully() {
        when(redisManager.getJedis()).thenThrow(new RuntimeException("Redis down"));

        // Should not throw
        assertDoesNotThrow(() -> cacheService.removeCachedSession("tok123"));
    }

    // ========== Session serialization round-trip ==========

    @Test
    void sessionSerializationRoundTrip_email() {
        Session original = new Session("token-abc", "uid-1", "Bob", "bob@example.com", "email", 1700000000L, 1700000000000L, false);
        String json = gson.toJson(original);
        Session deserialized = gson.fromJson(json, Session.class);
        assertEquals(original, deserialized);
    }

    @Test
    void sessionSerializationRoundTrip_feishu() {
        Session original = new Session("token-xyz", "uid-2", "Charlie", "charlie@corp.com", "feishu", 1800000000L, 1700000000000L, false);
        String json = gson.toJson(original);
        Session deserialized = gson.fromJson(json, Session.class);
        assertEquals(original, deserialized);
    }

    // ========== Skill list caching tests ==========

    @Test
    void cacheSkillList_shouldSetCorrectKeyAndTTL() {
        List<Skill> skills = List.of(
            new Skill("s1", "Translator", "Translate text", "icon.png", "翻译",
                true, "guide", List.of("example"), null, "test", "en", "basic",
                SkillType.GENERAL, SkillKind.PROMPT_ONLY, "template",
                SkillExecutionMode.SINGLE)
        );

        cacheService.cacheSkillList(skills, 600);

        String expectedJson = gson.toJson(skills);
        verify(jedis).set(eq("skills:all"), eq(expectedJson), any(SetParams.class));
    }

    @Test
    void getCachedSkillList_shouldReturnSkillsWhenPresent() {
        List<Skill> skills = List.of(
            new Skill("s1", "Translator", "Translate text", "icon.png", "翻译",
                true, "guide", List.of("example1", "example2"), null, "test", "en", "basic",
                SkillType.GENERAL, SkillKind.PROMPT_ONLY, "template",
                SkillExecutionMode.SINGLE),
            new Skill("s2", "Coder", "Code assistant", "code.png", "开发",
                true, null, List.of(), null, "test", "en", "basic",
                SkillType.INTERNAL, SkillKind.KNOWLEDGE_RAG, "code template",
                SkillExecutionMode.MULTI)
        );
        String json = gson.toJson(skills);
        when(jedis.get("skills:all")).thenReturn(json);

        Optional<List<Skill>> result = cacheService.getCachedSkillList();

        assertTrue(result.isPresent());
        assertEquals(2, result.get().size());
        assertEquals("Translator", result.get().get(0).name());
        assertEquals("Coder", result.get().get(1).name());
        assertEquals(SkillType.INTERNAL, result.get().get(1).type());
        assertEquals(SkillKind.KNOWLEDGE_RAG, result.get().get(1).kind());
    }

    @Test
    void getCachedSkillList_shouldReturnEmptyWhenNotPresent() {
        when(jedis.get("skills:all")).thenReturn(null);

        Optional<List<Skill>> result = cacheService.getCachedSkillList();

        assertTrue(result.isEmpty());
    }

    @Test
    void getCachedSkillList_shouldReturnEmptyOnRedisFailure() {
        when(redisManager.getJedis()).thenThrow(new RuntimeException("Redis down"));

        Optional<List<Skill>> result = cacheService.getCachedSkillList();

        assertTrue(result.isEmpty());
    }

    @Test
    void invalidateSkillCache_shouldDeleteCorrectKey() {
        cacheService.invalidateSkillCache();

        verify(jedis).del("skills:all");
    }

    @Test
    void invalidateSkillCache_shouldHandleRedisFailureGracefully() {
        when(redisManager.getJedis()).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> cacheService.invalidateSkillCache());
    }

    // ========== Skill list serialization round-trip ==========

    @Test
    void skillListSerializationRoundTrip() {
        List<Skill> original = List.of(
            new Skill("s1", "翻译助手", "翻译文本", "translate.png", "翻译",
                true, "使用说明", List.of("翻译这段话", "Translate this"),
                null, "test", "zh", "basic",
                SkillType.GENERAL, SkillKind.PROMPT_ONLY, "你是一个翻译助手", SkillExecutionMode.SINGLE),
            new Skill("s2", "内部知识库", "公司知识查询", null, "内部",
                false, null, List.of(),
                null, "test", "zh", "basic",
                SkillType.INTERNAL, SkillKind.KNOWLEDGE_RAG, "基于知识库回答", SkillExecutionMode.MULTI)
        );
        String json = gson.toJson(original);
        Type listType = new TypeToken<List<Skill>>() {}.getType();
        List<Skill> deserialized = gson.fromJson(json, listType);
        assertEquals(original, deserialized);
    }

    // ========== Knowledge text caching tests ==========

    @Test
    void cacheKnowledgeText_shouldSetCorrectKeyAndTTL() {
        cacheService.cacheKnowledgeText("skill-1", "merged knowledge text content", 1800);

        verify(jedis).set(eq("knowledge:skill-1"), eq("merged knowledge text content"), any(SetParams.class));
    }

    @Test
    void getCachedKnowledgeText_shouldReturnTextWhenPresent() {
        when(jedis.get("knowledge:skill-1")).thenReturn("cached knowledge text");

        Optional<String> result = cacheService.getCachedKnowledgeText("skill-1");

        assertTrue(result.isPresent());
        assertEquals("cached knowledge text", result.get());
    }

    @Test
    void getCachedKnowledgeText_shouldReturnEmptyWhenNotPresent() {
        when(jedis.get("knowledge:skill-1")).thenReturn(null);

        Optional<String> result = cacheService.getCachedKnowledgeText("skill-1");

        assertTrue(result.isEmpty());
    }

    @Test
    void getCachedKnowledgeText_shouldReturnEmptyOnRedisFailure() {
        when(redisManager.getJedis()).thenThrow(new RuntimeException("Redis down"));

        Optional<String> result = cacheService.getCachedKnowledgeText("skill-1");

        assertTrue(result.isEmpty());
    }

    @Test
    void invalidateKnowledgeCache_shouldDeleteCorrectKey() {
        cacheService.invalidateKnowledgeCache("skill-1");

        verify(jedis).del("knowledge:skill-1");
    }

    @Test
    void invalidateKnowledgeCache_shouldHandleRedisFailureGracefully() {
        when(redisManager.getJedis()).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> cacheService.invalidateKnowledgeCache("skill-1"));
    }

    // ========== Key pattern verification ==========

    @Test
    void keyPatterns_shouldMatchDesignSpec() {
        assertEquals("session:", CacheServiceImpl.SESSION_KEY_PREFIX);
        assertEquals("skills:all", CacheServiceImpl.SKILLS_ALL_KEY);
        assertEquals("knowledge:", CacheServiceImpl.KNOWLEDGE_KEY_PREFIX);
    }

    // ========== Graceful failure on cache write ==========

    @Test
    void cacheSession_shouldHandleRedisFailureGracefully() {
        when(redisManager.getJedis()).thenThrow(new RuntimeException("Redis down"));
        Session session = new Session("tok", "u1", "User", "u@t.com", "email", 100L, 1700000000000L, false);

        assertDoesNotThrow(() -> cacheService.cacheSession("tok", session, 600));
    }

    @Test
    void cacheSkillList_shouldHandleRedisFailureGracefully() {
        when(redisManager.getJedis()).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> cacheService.cacheSkillList(List.of(), 600));
    }

    @Test
    void cacheKnowledgeText_shouldHandleRedisFailureGracefully() {
        when(redisManager.getJedis()).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> cacheService.cacheKnowledgeText("s1", "text", 600));
    }
}
