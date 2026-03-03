package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillType;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Skill template required field validation.
 *
 * <p>Feature: yeahmobi-everything, Property 27: Skill 模板必填字段验证</p>
 */
class SkillTemplateValidationPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 27: Skill 模板必填字段验证
    void createSkillFromTemplate_rejectsInvalidTemplate(
            @ForAll("invalidTemplates") SkillTemplate template) {
        // **Validates: Requirements 15.7**

        List<SkillAdmin> store = new ArrayList<>();

        SkillRepository skillRepo = mock(SkillRepository.class);
        FeedbackRepository feedbackRepo = mock(FeedbackRepository.class);
        CacheService cacheService = mock(CacheService.class);

        doAnswer(inv -> {
            store.add(inv.getArgument(0));
            return null;
        }).when(skillRepo).saveSkill(any());

        AdminServiceImpl service = new AdminServiceImpl(skillRepo, feedbackRepo, cacheService);

        SkillIntegrationResult result = service.createSkillFromTemplate(template);

        assertFalse(result.success(), "createSkillFromTemplate should fail for template missing required fields");
        assertNull(result.skill(), "No skill should be created for invalid template");
        assertTrue(store.isEmpty(), "No skill should be saved to repository for invalid template");
    }

    @Provide
    Arbitrary<SkillTemplate> invalidTemplates() {
        // Generate templates where at least one required field (name, description, category) is blank/null
        Arbitrary<String> validStr = Arbitraries.strings().ofMinLength(1).ofMaxLength(10).alpha();
        Arbitrary<String> blankStr = Arbitraries.of(null, "", "   ");
        Arbitrary<SkillType> types = Arbitraries.of(SkillType.GENERAL, SkillType.INTERNAL);

        // Missing name
        Arbitrary<SkillTemplate> missingName = Combinators.combine(blankStr, validStr, validStr, types)
                .as((name, desc, cat, type) -> new SkillTemplate(name, desc, cat, "icon.png", type,
                        "prompt", SkillExecutionMode.SINGLE));

        // Missing description
        Arbitrary<SkillTemplate> missingDesc = Combinators.combine(validStr, blankStr, validStr, types)
                .as((name, desc, cat, type) -> new SkillTemplate(name, desc, cat, "icon.png", type,
                        "prompt", SkillExecutionMode.SINGLE));

        // Missing category
        Arbitrary<SkillTemplate> missingCat = Combinators.combine(validStr, validStr, blankStr, types)
                .as((name, desc, cat, type) -> new SkillTemplate(name, desc, cat, "icon.png", type,
                        "prompt", SkillExecutionMode.SINGLE));

        return Arbitraries.oneOf(missingName, missingDesc, missingCat);
    }
}
