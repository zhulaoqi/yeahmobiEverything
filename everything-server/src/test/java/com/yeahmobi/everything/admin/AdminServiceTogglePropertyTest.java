package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.feedback.Feedback;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Skill status toggle consistency.
 *
 * <p>Feature: yeahmobi-everything, Property 12: Skill 状态切换一致性</p>
 */
class AdminServiceTogglePropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 12: Skill 状态切换一致性
    void toggleSkillStatus_updatesEnabledField(
            @ForAll("skillAdmins") SkillAdmin skill,
            @ForAll boolean targetEnabled) {
        // **Validates: Requirements 5.4**

        // In-memory store to simulate repository
        Map<String, SkillAdmin> store = new HashMap<>();
        store.put(skill.id(), skill);

        SkillRepository skillRepo = mock(SkillRepository.class);
        FeedbackRepository feedbackRepo = mock(FeedbackRepository.class);
        CacheService cacheService = mock(CacheService.class);

        when(skillRepo.getSkill(skill.id())).thenAnswer(inv -> Optional.ofNullable(store.get(skill.id())));
        doAnswer(inv -> {
            SkillAdmin updated = inv.getArgument(0);
            store.put(updated.id(), updated);
            return null;
        }).when(skillRepo).updateSkill(any());

        AdminServiceImpl service = new AdminServiceImpl(skillRepo, feedbackRepo, cacheService);

        // Toggle status
        service.toggleSkillStatus(skill.id(), targetEnabled);

        // Verify the stored skill has the target enabled state
        SkillAdmin result = store.get(skill.id());
        assertNotNull(result);
        assertEquals(targetEnabled, result.enabled(),
                "After toggleSkillStatus(" + targetEnabled + "), enabled should be " + targetEnabled);

        // Verify other fields are unchanged
        assertEquals(skill.id(), result.id());
        assertEquals(skill.name(), result.name());
        assertEquals(skill.description(), result.description());
        assertEquals(skill.category(), result.category());
        assertEquals(skill.type(), result.type());
        assertEquals(skill.kind(), result.kind());
        assertEquals(skill.promptTemplate(), result.promptTemplate());
    }

    // ---- Arbitrary Providers ----

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
                                type, kind, "prompt", SkillExecutionMode.SINGLE, System.currentTimeMillis()));
    }
}
