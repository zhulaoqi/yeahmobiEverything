package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.FavoriteRepository;
import com.yeahmobi.everything.repository.local.UsageRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import net.jqwik.api.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SkillService category filtering.
 *
 * <p>Feature: yeahmobi-everything, Property 5: Skill 分类筛选正确性</p>
 */
class SkillCategoryFilterPropertyTest {

    private SkillServiceImpl createSkillService() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        CacheService cacheService = mock(CacheService.class);
        FavoriteRepository favoriteRepository = mock(FavoriteRepository.class);
        UsageRepository usageRepository = mock(UsageRepository.class);
        com.yeahmobi.everything.repository.local.ChatRepository chatRepository = mock(com.yeahmobi.everything.repository.local.ChatRepository.class);
        return new SkillServiceImpl(skillRepository, cacheService, favoriteRepository, usageRepository, chatRepository);
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 5: Skill 分类筛选正确性
    void filterByCategory_allResultsHaveMatchingCategory(
            @ForAll("skillLists") List<Skill> allSkills,
            @ForAll("categories") String category) {
        // **Validates: Requirements 2.4**
        // (a) 结果中每个 Skill 的 category 字段应与筛选分类一致

        SkillServiceImpl service = createSkillService();
        List<Skill> results = service.filterByCategory(category, allSkills);

        for (Skill skill : results) {
            assertEquals(category, skill.category(),
                    "Result skill '" + skill.name() + "' has category '" + skill.category()
                            + "' but expected '" + category + "'");
        }
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 5: Skill 分类筛选正确性
    void filterByCategory_allMatchingSkillsAreInResults(
            @ForAll("skillLists") List<Skill> allSkills,
            @ForAll("categories") String category) {
        // **Validates: Requirements 2.4**
        // (b) 原列表中属于该分类的 Skill 都出现在结果中

        SkillServiceImpl service = createSkillService();
        List<Skill> results = service.filterByCategory(category, allSkills);

        List<Skill> expectedMatches = allSkills.stream()
                .filter(skill -> category.equals(skill.category()))
                .collect(Collectors.toList());

        for (Skill expected : expectedMatches) {
            assertTrue(results.contains(expected),
                    "Skill '" + expected.name() + "' has category '" + category
                            + "' but is missing from filter results");
        }

        // Also verify the result size matches expected count (soundness + completeness)
        assertEquals(expectedMatches.size(), results.size(),
                "Filter results size should match the number of skills in the category");
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<List<Skill>> skillLists() {
        return skills().list().ofMinSize(0).ofMaxSize(20);
    }

    @Provide
    Arbitrary<Skill> skills() {
        Arbitrary<String> ids = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(10).alpha().numeric();
        Arbitrary<String> names = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(30)
                .withChars('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
                        '翻', '译', '助', '手', '代', '码', '写', '作', '数', '据');
        Arbitrary<String> descriptions = Arbitraries.strings()
                .ofMinLength(0).ofMaxLength(50)
                .withChars('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
                        '帮', '助', '翻', '译', '文', '本', '编', '写', '代', '码');
        Arbitrary<String> categories = Arbitraries.of("翻译", "写作", "开发", "数据", "其他");
        Arbitrary<SkillType> types = Arbitraries.of(SkillType.GENERAL, SkillType.INTERNAL);
        Arbitrary<SkillKind> kinds = Arbitraries.of(SkillKind.PROMPT_ONLY, SkillKind.KNOWLEDGE_RAG);

        return Combinators.combine(ids, names, descriptions, categories, types, kinds)
                .as((id, name, desc, cat, type, kind) ->
                        new Skill(id, name, desc, "icon.png", cat, true, "",
                                List.of(), null, "test", "en", "basic",
                                type, kind, "prompt", SkillExecutionMode.SINGLE));
    }

    @Provide
    Arbitrary<String> categories() {
        // Use the same category set as the skill generator, plus a random category
        // that may not exist in any skill to test empty results
        return Arbitraries.oneOf(
                Arbitraries.of("翻译", "写作", "开发", "数据", "其他"),
                Arbitraries.strings().ofMinLength(1).ofMaxLength(10)
                        .withChars('a', 'b', 'c', '测', '试', '分', '类')
        );
    }
}
