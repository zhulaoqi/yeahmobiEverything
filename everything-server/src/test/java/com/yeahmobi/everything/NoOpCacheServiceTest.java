package com.yeahmobi.everything;

import com.yeahmobi.everything.auth.Session;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.skill.Skill;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NoOpCacheService}.
 * Verifies that all cache operations are no-ops and reads return empty results.
 */
class NoOpCacheServiceTest {

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new NoOpCacheService();
    }

    @Test
    void getCachedSessionReturnsEmpty() {
        Optional<Session> result = cacheService.getCachedSession("any-token");
        assertTrue(result.isEmpty(), "NoOp cache should return empty for session lookup");
    }

    @Test
    void cacheSessionDoesNotThrow() {
        Session session = new Session("token", "user1", "Test User", "test@example.com", "email",
                System.currentTimeMillis() + 86400000, 1700000000000L, false);
        assertDoesNotThrow(() -> cacheService.cacheSession("token", session, 3600));
    }

    @Test
    void removeCachedSessionDoesNotThrow() {
        assertDoesNotThrow(() -> cacheService.removeCachedSession("any-token"));
    }

    @Test
    void getCachedSkillListReturnsEmpty() {
        Optional<List<Skill>> result = cacheService.getCachedSkillList();
        assertTrue(result.isEmpty(), "NoOp cache should return empty for skill list lookup");
    }

    @Test
    void cacheSkillListDoesNotThrow() {
        Skill skill = new Skill("1", "Test", "desc", "icon", "cat", true, null, List.of(),
                null, "test", "en", "basic",
                SkillType.GENERAL, SkillKind.PROMPT_ONLY, null, SkillExecutionMode.SINGLE);
        assertDoesNotThrow(() -> cacheService.cacheSkillList(List.of(skill), 600));
    }

    @Test
    void invalidateSkillCacheDoesNotThrow() {
        assertDoesNotThrow(() -> cacheService.invalidateSkillCache());
    }

    @Test
    void getCachedKnowledgeTextReturnsEmpty() {
        Optional<String> result = cacheService.getCachedKnowledgeText("skill-1");
        assertTrue(result.isEmpty(), "NoOp cache should return empty for knowledge text lookup");
    }

    @Test
    void cacheKnowledgeTextDoesNotThrow() {
        assertDoesNotThrow(() -> cacheService.cacheKnowledgeText("skill-1", "some text", 1800));
    }

    @Test
    void invalidateKnowledgeCacheDoesNotThrow() {
        assertDoesNotThrow(() -> cacheService.invalidateKnowledgeCache("skill-1"));
    }
}
