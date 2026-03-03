package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.FavoriteRepository;
import com.yeahmobi.everything.repository.local.UsageRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import net.jqwik.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Skill type filtering correctness.
 *
 * <p>Feature: yeahmobi-everything, Property 30: Skill 类型筛选正确性</p>
 */
class SkillTypeFilterPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 30: Skill 类型筛选正确性
    void filterByType_returnsOnlyMatchingType(
            @ForAll("skillLists") List<Skill> allSkills,
            @ForAll("skillTypes") SkillType filterType) {
        // **Validates: Requirements 17.2**

        SkillRepository skillRepo = mock(SkillRepository.class);
        CacheService cacheService = mock(CacheService.class);
        FavoriteRepository favRepo = mock(FavoriteRepository.class);
        UsageRepository usageRepo = mock(UsageRepository.class);
        com.yeahmobi.everything.repository.local.ChatRepository chatRepo = mock(com.yeahmobi.everything.repository.local.ChatRepository.class);

        SkillServiceImpl service = new SkillServiceImpl(skillRepo, cacheService, favRepo, usageRepo, chatRepo);

        List<Skill> result = service.filterByType(filterType, allSkills);

        // (a) Every result skill has the matching type
        for (Skill skill : result) {
            assertEquals(filterType, skill.type(),
                    "Filtered skill should have type " + filterType);
        }

        // (b) All skills of that type from the original list appear in the result
        long expectedCount = allSkills.stream()
                .filter(s -> filterType.equals(s.type()))
                .count();
        assertEquals(expectedCount, result.size(),
                "Result should contain all skills of type " + filterType);
    }

    @Provide
    Arbitrary<List<Skill>> skillLists() {
        return skills().list().ofMinSize(0).ofMaxSize(15);
    }

    @Provide
    Arbitrary<Skill> skills() {
        Arbitrary<String> ids = Arbitraries.strings().ofMinLength(1).ofMaxLength(8).alpha();
        Arbitrary<String> names = Arbitraries.strings().ofMinLength(1).ofMaxLength(10).alpha();
        Arbitrary<String> descs = Arbitraries.strings().ofMinLength(1).ofMaxLength(15).alpha();
        Arbitrary<String> categories = Arbitraries.of("翻译", "写作", "开发", "数据");
        Arbitrary<SkillType> types = Arbitraries.of(SkillType.GENERAL, SkillType.INTERNAL);
        Arbitrary<SkillKind> kinds = Arbitraries.of(SkillKind.PROMPT_ONLY, SkillKind.KNOWLEDGE_RAG);

        return Combinators.combine(ids, names, descs, categories, types, kinds)
                .as((id, name, desc, cat, type, kind) ->
                        new Skill(id, name, desc, "icon.png", cat, true, "", List.of(),
                                null, "test", "en", "basic",
                                type, kind, "", SkillExecutionMode.SINGLE));
    }

    @Provide
    Arbitrary<SkillType> skillTypes() {
        return Arbitraries.of(SkillType.GENERAL, SkillType.INTERNAL);
    }
}
