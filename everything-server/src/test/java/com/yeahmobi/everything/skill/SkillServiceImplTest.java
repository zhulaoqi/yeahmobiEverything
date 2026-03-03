package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.common.NetworkException;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.FavoriteRepository;
import com.yeahmobi.everything.repository.local.UsageRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SkillServiceImpl}.
 * <p>
 * Uses Mockito to mock SkillRepository, CacheService, FavoriteRepository,
 * and UsageRepository dependencies.
 * Tests cover fetching (cache hit/miss), search, filtering, favorites,
 * usage tracking, and default skills.
 * </p>
 */
class SkillServiceImplTest {

    private SkillRepository skillRepository;
    private CacheService cacheService;
    private FavoriteRepository favoriteRepository;
    private UsageRepository usageRepository;
    private SkillServiceImpl skillService;

    @BeforeEach
    void setUp() {
        skillRepository = mock(SkillRepository.class);
        cacheService = mock(CacheService.class);
        favoriteRepository = mock(FavoriteRepository.class);
        usageRepository = mock(UsageRepository.class);
        com.yeahmobi.everything.repository.local.ChatRepository chatRepository = mock(com.yeahmobi.everything.repository.local.ChatRepository.class);
        skillService = new SkillServiceImpl(skillRepository, cacheService,
                favoriteRepository, usageRepository, chatRepository);
    }

    // ---- fetchSkills tests ----

    @Test
    void fetchSkillsReturnsCachedListWhenAvailable() throws NetworkException {
        List<Skill> cachedSkills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL),
                createSkill("s2", "代码助手", "开发", SkillType.GENERAL)
        );
        when(cacheService.getCachedSkillList()).thenReturn(Optional.of(cachedSkills));

        List<Skill> result = skillService.fetchSkills();

        assertEquals(2, result.size());
        assertEquals("翻译助手", result.get(0).name());
        // Should NOT query the database when cache is available
        verify(skillRepository, never()).getAllSkills();
    }

    @Test
    void fetchSkillsQueriesDatabaseWhenCacheEmpty() throws NetworkException {
        when(cacheService.getCachedSkillList()).thenReturn(Optional.empty());
        List<SkillAdmin> dbSkills = List.of(
                createSkillAdmin("s1", "翻译助手", "翻译", true, SkillType.GENERAL, SkillKind.PROMPT_ONLY),
                createSkillAdmin("s2", "代码助手", "开发", true, SkillType.GENERAL, SkillKind.PROMPT_ONLY)
        );
        when(skillRepository.getAllSkills()).thenReturn(dbSkills);

        List<Skill> result = skillService.fetchSkills();

        assertEquals(2, result.size());
        verify(skillRepository).getAllSkills();
        // Should cache the result
        verify(cacheService).cacheSkillList(anyList(), eq(SkillServiceImpl.SKILL_CACHE_TTL_SECONDS));
    }

    @Test
    void fetchSkillsFiltersOutDisabledSkills() throws NetworkException {
        when(cacheService.getCachedSkillList()).thenReturn(Optional.empty());
        List<SkillAdmin> dbSkills = List.of(
                createSkillAdmin("s1", "启用的", "翻译", true, SkillType.GENERAL, SkillKind.PROMPT_ONLY),
                createSkillAdmin("s2", "禁用的", "开发", false, SkillType.GENERAL, SkillKind.PROMPT_ONLY),
                createSkillAdmin("s3", "也启用的", "写作", true, SkillType.INTERNAL, SkillKind.KNOWLEDGE_RAG)
        );
        when(skillRepository.getAllSkills()).thenReturn(dbSkills);

        List<Skill> result = skillService.fetchSkills();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(Skill::enabled));
        assertTrue(result.stream().noneMatch(s -> s.name().equals("禁用的")));
    }

    @Test
    void fetchSkillsFallsToDatabaseWhenCacheThrowsException() throws NetworkException {
        when(cacheService.getCachedSkillList()).thenThrow(new RuntimeException("Redis down"));
        List<SkillAdmin> dbSkills = List.of(
                createSkillAdmin("s1", "翻译助手", "翻译", true, SkillType.GENERAL, SkillKind.PROMPT_ONLY)
        );
        when(skillRepository.getAllSkills()).thenReturn(dbSkills);

        List<Skill> result = skillService.fetchSkills();

        assertEquals(1, result.size());
        verify(skillRepository).getAllSkills();
    }

    @Test
    void fetchSkillsThrowsNetworkExceptionWhenDatabaseFails() {
        when(cacheService.getCachedSkillList()).thenReturn(Optional.empty());
        when(skillRepository.getAllSkills()).thenThrow(new RuntimeException("DB connection failed"));

        assertThrows(NetworkException.class, () -> skillService.fetchSkills());
    }

    // ---- searchSkills tests ----

    @Test
    void searchSkillsMatchesNameCaseInsensitive() {
        List<Skill> skills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL),
                createSkill("s2", "代码助手", "开发", SkillType.GENERAL),
                createSkill("s3", "Translation Helper", "翻译", SkillType.GENERAL)
        );

        List<Skill> result = skillService.searchSkills("translation", skills);

        assertEquals(1, result.size());
        assertEquals("Translation Helper", result.get(0).name());
    }

    @Test
    void searchSkillsMatchesDescription() {
        List<Skill> skills = List.of(
                createSkill("s1", "工具A", "帮助翻译文本", "翻译", SkillType.GENERAL),
                createSkill("s2", "工具B", "帮助编写代码", "开发", SkillType.GENERAL)
        );

        List<Skill> result = skillService.searchSkills("翻译", skills);

        assertEquals(1, result.size());
        assertEquals("工具A", result.get(0).name());
    }

    @Test
    void searchSkillsReturnsAllWhenKeywordIsNull() {
        List<Skill> skills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL),
                createSkill("s2", "代码助手", "开发", SkillType.GENERAL)
        );

        List<Skill> result = skillService.searchSkills(null, skills);

        assertEquals(2, result.size());
    }

    @Test
    void searchSkillsReturnsAllWhenKeywordIsBlank() {
        List<Skill> skills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL),
                createSkill("s2", "代码助手", "开发", SkillType.GENERAL)
        );

        List<Skill> result = skillService.searchSkills("  ", skills);

        assertEquals(2, result.size());
    }

    @Test
    void searchSkillsReturnsEmptyWhenNoMatch() {
        List<Skill> skills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL)
        );

        List<Skill> result = skillService.searchSkills("不存在的关键词", skills);

        assertTrue(result.isEmpty());
    }

    // ---- filterByCategory tests ----

    @Test
    void filterByCategoryReturnsMatchingSkills() {
        List<Skill> skills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL),
                createSkill("s2", "代码助手", "开发", SkillType.GENERAL),
                createSkill("s3", "文案撰写", "写作", SkillType.GENERAL)
        );

        List<Skill> result = skillService.filterByCategory("翻译", skills);

        assertEquals(1, result.size());
        assertEquals("翻译助手", result.get(0).name());
    }

    @Test
    void filterByCategoryReturnsAllWhenCategoryIsNull() {
        List<Skill> skills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL),
                createSkill("s2", "代码助手", "开发", SkillType.GENERAL)
        );

        List<Skill> result = skillService.filterByCategory(null, skills);

        assertEquals(2, result.size());
    }

    @Test
    void filterByCategoryReturnsAllWhenCategoryIsBlank() {
        List<Skill> skills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL),
                createSkill("s2", "代码助手", "开发", SkillType.GENERAL)
        );

        List<Skill> result = skillService.filterByCategory("", skills);

        assertEquals(2, result.size());
    }

    @Test
    void filterByCategoryReturnsEmptyWhenNoMatch() {
        List<Skill> skills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL)
        );

        List<Skill> result = skillService.filterByCategory("不存在的分类", skills);

        assertTrue(result.isEmpty());
    }

    // ---- filterByType tests ----

    @Test
    void filterByTypeReturnsMatchingSkills() {
        List<Skill> skills = List.of(
                createSkill("s1", "通用Skill", "翻译", SkillType.GENERAL),
                createSkill("s2", "内部Skill", "开发", SkillType.INTERNAL),
                createSkill("s3", "另一个通用", "写作", SkillType.GENERAL)
        );

        List<Skill> result = skillService.filterByType(SkillType.INTERNAL, skills);

        assertEquals(1, result.size());
        assertEquals("内部Skill", result.get(0).name());
    }

    @Test
    void filterByTypeReturnsAllWhenTypeIsNull() {
        List<Skill> skills = List.of(
                createSkill("s1", "通用Skill", "翻译", SkillType.GENERAL),
                createSkill("s2", "内部Skill", "开发", SkillType.INTERNAL)
        );

        List<Skill> result = skillService.filterByType(null, skills);

        assertEquals(2, result.size());
    }

    @Test
    void filterByTypeReturnsGeneralSkills() {
        List<Skill> skills = List.of(
                createSkill("s1", "通用Skill", "翻译", SkillType.GENERAL),
                createSkill("s2", "内部Skill", "开发", SkillType.INTERNAL),
                createSkill("s3", "另一个通用", "写作", SkillType.GENERAL)
        );

        List<Skill> result = skillService.filterByType(SkillType.GENERAL, skills);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.type() == SkillType.GENERAL));
    }

    // ---- getDefaultSkills tests ----

    @Test
    void getDefaultSkillsReturnsFourSkills() {
        List<Skill> defaults = skillService.getDefaultSkills();

        assertEquals(4, defaults.size());
    }

    @Test
    void getDefaultSkillsContainsExpectedNames() {
        List<Skill> defaults = skillService.getDefaultSkills();
        List<String> names = defaults.stream().map(Skill::name).toList();

        assertTrue(names.contains("翻译助手"), "Should contain 翻译助手");
        assertTrue(names.contains("文案撰写"), "Should contain 文案撰写");
        assertTrue(names.contains("代码助手"), "Should contain 代码助手");
        assertTrue(names.contains("数据分析"), "Should contain 数据分析");
    }

    @Test
    void getDefaultSkillsAreAllEnabled() {
        List<Skill> defaults = skillService.getDefaultSkills();

        assertTrue(defaults.stream().allMatch(Skill::enabled));
    }

    @Test
    void getDefaultSkillsAreAllGeneralType() {
        List<Skill> defaults = skillService.getDefaultSkills();

        assertTrue(defaults.stream().allMatch(s -> s.type() == SkillType.GENERAL));
    }

    @Test
    void getDefaultSkillsAreAllPromptOnly() {
        List<Skill> defaults = skillService.getDefaultSkills();

        assertTrue(defaults.stream().allMatch(s -> s.kind() == SkillKind.PROMPT_ONLY));
    }

    @Test
    void getDefaultSkillsHaveNonEmptyFields() {
        List<Skill> defaults = skillService.getDefaultSkills();

        for (Skill skill : defaults) {
            assertNotNull(skill.id(), "id should not be null");
            assertFalse(skill.id().isBlank(), "id should not be blank");
            assertNotNull(skill.name(), "name should not be null");
            assertFalse(skill.name().isBlank(), "name should not be blank");
            assertNotNull(skill.description(), "description should not be null");
            assertFalse(skill.description().isBlank(), "description should not be blank");
            assertNotNull(skill.category(), "category should not be null");
            assertFalse(skill.category().isBlank(), "category should not be blank");
            assertNotNull(skill.promptTemplate(), "promptTemplate should not be null");
            assertFalse(skill.promptTemplate().isBlank(), "promptTemplate should not be blank");
        }
    }

    // ---- getFavorites tests ----

    @Test
    void getFavoritesReturnsFavoriteSkills() {
        List<Skill> cachedSkills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL),
                createSkill("s2", "代码助手", "开发", SkillType.GENERAL),
                createSkill("s3", "文案撰写", "写作", SkillType.GENERAL)
        );
        when(cacheService.getCachedSkillList()).thenReturn(Optional.of(cachedSkills));
        when(favoriteRepository.getFavoriteSkillIds("user-1")).thenReturn(List.of("s1", "s3"));

        List<Skill> result = skillService.getFavorites("user-1");

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.id().equals("s1")));
        assertTrue(result.stream().anyMatch(s -> s.id().equals("s3")));
    }

    @Test
    void getFavoritesReturnsEmptyWhenNoFavorites() {
        when(favoriteRepository.getFavoriteSkillIds("user-1")).thenReturn(List.of());

        List<Skill> result = skillService.getFavorites("user-1");

        assertTrue(result.isEmpty());
    }

    // ---- toggleFavorite tests ----

    @Test
    void toggleFavoriteAddsWhenNotFavorite() {
        when(favoriteRepository.isFavorite("user-1", "skill-1")).thenReturn(false);

        skillService.toggleFavorite("user-1", "skill-1");

        verify(favoriteRepository).addFavorite("user-1", "skill-1");
        verify(favoriteRepository, never()).removeFavorite(anyString(), anyString());
    }

    @Test
    void toggleFavoriteRemovesWhenAlreadyFavorite() {
        when(favoriteRepository.isFavorite("user-1", "skill-1")).thenReturn(true);

        skillService.toggleFavorite("user-1", "skill-1");

        verify(favoriteRepository).removeFavorite("user-1", "skill-1");
        verify(favoriteRepository, never()).addFavorite(anyString(), anyString());
    }

    // ---- getRecentlyUsed tests ----

    @Test
    void getRecentlyUsedReturnsSkillsInOrder() {
        List<Skill> cachedSkills = List.of(
                createSkill("s1", "翻译助手", "翻译", SkillType.GENERAL),
                createSkill("s2", "代码助手", "开发", SkillType.GENERAL),
                createSkill("s3", "文案撰写", "写作", SkillType.GENERAL)
        );
        when(cacheService.getCachedSkillList()).thenReturn(Optional.of(cachedSkills));
        when(usageRepository.getRecentSkillIds("user-1", 5)).thenReturn(List.of("s3", "s1"));

        List<Skill> result = skillService.getRecentlyUsed("user-1", 5);

        assertEquals(2, result.size());
        assertEquals("s3", result.get(0).id());
        assertEquals("s1", result.get(1).id());
    }

    @Test
    void getRecentlyUsedReturnsEmptyWhenNoUsage() {
        when(usageRepository.getRecentSkillIds("user-1", 5)).thenReturn(List.of());

        List<Skill> result = skillService.getRecentlyUsed("user-1", 5);

        assertTrue(result.isEmpty());
    }

    // ---- recordUsage tests ----

    @Test
    void recordUsageDelegatesToRepository() {
        skillService.recordUsage("user-1", "skill-1");

        verify(usageRepository).recordUsage("user-1", "skill-1");
    }

    // ---- toSkill conversion test ----

    @Test
    void toSkillConvertsSkillAdminCorrectly() {
        SkillAdmin admin = createSkillAdmin("s1", "翻译助手", "翻译", true,
                SkillType.INTERNAL, SkillKind.KNOWLEDGE_RAG);

        Skill skill = skillService.toSkill(admin);

        assertEquals(admin.id(), skill.id());
        assertEquals(admin.name(), skill.name());
        assertEquals(admin.description(), skill.description());
        assertEquals(admin.icon(), skill.icon());
        assertEquals(admin.category(), skill.category());
        assertEquals(admin.enabled(), skill.enabled());
        assertEquals(admin.type(), skill.type());
        assertEquals(admin.kind(), skill.kind());
        assertEquals(admin.promptTemplate(), skill.promptTemplate());
        assertEquals(admin.executionMode(), skill.executionMode());
        assertEquals(admin.usageGuide(), skill.usageGuide());
        assertEquals(admin.examples(), skill.examples());
        assertEquals(admin.i18nJson(), skill.i18nJson());
        assertEquals(admin.source(), skill.source());
        assertEquals(admin.sourceLang(), skill.sourceLang());
        assertEquals(admin.qualityTier(), skill.qualityTier());
    }

    // ---- Helper methods ----

    private Skill createSkill(String id, String name, String category, SkillType type) {
        return new Skill(id, name, name + " description", "icon.png", category,
                true, "", List.of(), null, "test", "en", "basic",
                type, SkillKind.PROMPT_ONLY, "prompt", SkillExecutionMode.SINGLE);
    }

    private Skill createSkill(String id, String name, String description, String category, SkillType type) {
        return new Skill(id, name, description, "icon.png", category,
                true, "", List.of(), null, "test", "en", "basic",
                type, SkillKind.PROMPT_ONLY, "prompt", SkillExecutionMode.SINGLE);
    }

    private SkillAdmin createSkillAdmin(String id, String name, String category,
                                         boolean enabled, SkillType type, SkillKind kind) {
        return new SkillAdmin(id, name, name + " description", "icon.png", category,
                enabled,
                "Usage guide for " + name,
                List.of("Example 1 for " + name, "Example 2 for " + name),
                "{\"zh-CN\":{\"displayName\":\"" + name + "（中文）\"}}",
                "test",
                "en",
                "basic",
                type, kind, "prompt template", SkillExecutionMode.SINGLE, System.currentTimeMillis());
    }
}
