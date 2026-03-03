package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillType;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Skill template creation round-trip.
 *
 * <p>Feature: yeahmobi-everything, Property 26: Skill 模板创建 round-trip</p>
 */
class SkillTemplateCreateRoundTripPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 26: Skill 模板创建 round-trip
    void createSkillFromTemplate_roundTrip(@ForAll("validTemplates") SkillTemplate template) {
        // **Validates: Requirements 15.6, 15.10, 15.11**

        List<SkillAdmin> store = new ArrayList<>();

        SkillRepository skillRepo = mock(SkillRepository.class);
        FeedbackRepository feedbackRepo = mock(FeedbackRepository.class);
        CacheService cacheService = mock(CacheService.class);

        doAnswer(inv -> {
            store.add(inv.getArgument(0));
            return null;
        }).when(skillRepo).saveSkill(any());
        when(skillRepo.getAllSkills()).thenAnswer(inv -> new ArrayList<>(store));

        AdminServiceImpl service = new AdminServiceImpl(skillRepo, feedbackRepo, cacheService);

        // Create skill from template
        SkillIntegrationResult result = service.createSkillFromTemplate(template);

        // Verify success
        assertTrue(result.success(), "createSkillFromTemplate should succeed for valid template");
        assertNotNull(result.skill(), "Result should contain the created skill");

        // Verify the skill appears in getAllSkills
        List<SkillAdmin> allSkills = service.getAllSkills();
        assertEquals(1, allSkills.size(), "There should be exactly one skill");

        SkillAdmin created = allSkills.get(0);

        // Verify fields match the template
        assertEquals(template.name(), created.name());
        assertEquals(template.description(), created.description());
        assertEquals(template.category(), created.category());
        assertEquals(template.type(), created.type());
        assertTrue(created.enabled(), "Newly created skill should be enabled");
        assertEquals(SkillKind.PROMPT_ONLY, created.kind(), "Template-created skill should be PROMPT_ONLY");
    }

    @Provide
    Arbitrary<SkillTemplate> validTemplates() {
        Arbitrary<String> names = Arbitraries.strings().ofMinLength(1).ofMaxLength(15).alpha();
        Arbitrary<String> descs = Arbitraries.strings().ofMinLength(1).ofMaxLength(20).alpha();
        Arbitrary<String> categories = Arbitraries.of("翻译", "写作", "开发", "数据");
        Arbitrary<SkillType> types = Arbitraries.of(SkillType.GENERAL, SkillType.INTERNAL);
        Arbitrary<String> prompts = Arbitraries.strings().ofMinLength(0).ofMaxLength(30).alpha();

        return Combinators.combine(names, descs, categories, types, prompts)
                .as((name, desc, cat, type, prompt) ->
                        new SkillTemplate(name, desc, cat, "icon.png", type, prompt, SkillExecutionMode.SINGLE));
    }
}
