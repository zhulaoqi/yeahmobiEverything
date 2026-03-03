package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import com.yeahmobi.everything.skill.Skill;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Skill cache invalidation after mutations.
 *
 * <p>Feature: yeahmobi-everything, Property 36: Skill 变更后缓存失效</p>
 */
class SkillCacheInvalidationPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 36: Skill 变更后缓存失效
    void integrateSkill_invalidatesCache(
            @ForAll("validCommands") String command) {
        // **Validates: Requirements 16.4**

        // Track whether cache was populated and then invalidated
        List<Boolean> cacheState = new ArrayList<>();
        cacheState.add(true); // cache starts populated

        SkillRepository skillRepo = mock(SkillRepository.class);
        FeedbackRepository feedbackRepo = mock(FeedbackRepository.class);
        CacheService cacheService = mock(CacheService.class);

        when(cacheService.getCachedSkillList()).thenAnswer(inv ->
                cacheState.get(0) ? Optional.of(List.of()) : Optional.empty());
        doAnswer(inv -> {
            cacheState.set(0, false); // invalidate
            return null;
        }).when(cacheService).invalidateSkillCache();

        AdminServiceImpl service = new AdminServiceImpl(skillRepo, feedbackRepo, cacheService);

        // Verify cache is populated before
        assertTrue(cacheService.getCachedSkillList().isPresent(), "Cache should be populated before mutation");

        // Perform mutation
        service.integrateSkill(command);

        // Verify cache is invalidated after
        assertTrue(cacheState.get(0) == false || !cacheService.getCachedSkillList().isPresent(),
                "Cache should be invalidated after integrateSkill");
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 36: Skill 变更后缓存失效
    void toggleSkillStatus_invalidatesCache(
            @ForAll("skillAdmins") SkillAdmin skill,
            @ForAll boolean targetEnabled) {
        // **Validates: Requirements 16.4**

        Map<String, SkillAdmin> store = new HashMap<>();
        store.put(skill.id(), skill);
        List<Boolean> cachePopulated = new ArrayList<>();
        cachePopulated.add(true);

        SkillRepository skillRepo = mock(SkillRepository.class);
        FeedbackRepository feedbackRepo = mock(FeedbackRepository.class);
        CacheService cacheService = mock(CacheService.class);

        when(skillRepo.getSkill(skill.id())).thenReturn(Optional.of(skill));
        doAnswer(inv -> {
            store.put(((SkillAdmin) inv.getArgument(0)).id(), inv.getArgument(0));
            return null;
        }).when(skillRepo).updateSkill(any());

        when(cacheService.getCachedSkillList()).thenAnswer(inv ->
                cachePopulated.get(0) ? Optional.of(List.of()) : Optional.empty());
        doAnswer(inv -> {
            cachePopulated.set(0, false);
            return null;
        }).when(cacheService).invalidateSkillCache();

        AdminServiceImpl service = new AdminServiceImpl(skillRepo, feedbackRepo, cacheService);

        // Verify cache is populated before
        assertTrue(cacheService.getCachedSkillList().isPresent(), "Cache should be populated before toggle");

        // Perform mutation
        service.toggleSkillStatus(skill.id(), targetEnabled);

        // Verify cache is invalidated
        Optional<List<Skill>> cachedAfter = cacheService.getCachedSkillList();
        assertTrue(cachedAfter.isEmpty(), "getCachedSkillList should return empty after toggleSkillStatus");
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> validCommands() {
        Arbitrary<String> names = Arbitraries.of("翻译助手", "代码助手", "TestSkill", "DataHelper");
        Arbitrary<String> descs = Arbitraries.of("帮助翻译", "编写代码", "Test description", "Data analysis");
        Arbitrary<String> categories = Arbitraries.of("翻译", "开发", "数据", "写作");
        Arbitrary<SkillType> types = Arbitraries.of(SkillType.GENERAL, SkillType.INTERNAL);
        Arbitrary<SkillKind> kinds = Arbitraries.of(SkillKind.PROMPT_ONLY, SkillKind.KNOWLEDGE_RAG);

        return Combinators.combine(names, descs, categories, types, kinds)
                .as((name, desc, cat, type, kind) ->
                        "--name \"" + name + "\" --desc \"" + desc + "\" --category \"" + cat
                                + "\" --type " + type.name() + " --kind " + kind.name());
    }

    @Provide
    Arbitrary<SkillAdmin> skillAdmins() {
        Arbitrary<String> ids = Arbitraries.strings().ofMinLength(1).ofMaxLength(10).alpha();
        Arbitrary<String> names = Arbitraries.strings().ofMinLength(1).ofMaxLength(15).alpha();
        Arbitrary<String> descs = Arbitraries.strings().ofMinLength(1).ofMaxLength(20).alpha();
        Arbitrary<String> categories = Arbitraries.of("翻译", "写作", "开发", "数据");
        Arbitrary<Boolean> enableds = Arbitraries.of(true, false);
        Arbitrary<SkillType> types = Arbitraries.of(SkillType.GENERAL, SkillType.INTERNAL);
        Arbitrary<SkillKind> kinds = Arbitraries.of(SkillKind.PROMPT_ONLY, SkillKind.KNOWLEDGE_RAG);

        return Combinators.combine(ids, names, descs, categories, enableds, types, kinds)
                .as((id, name, desc, cat, enabled, type, kind) ->
                        new SkillAdmin(id, name, desc, "icon.png", cat, enabled,
                                "", List.of(), null, "test", "en", "basic",
                                type, kind, "prompt", SkillExecutionMode.SINGLE, 1000L));
    }
}
