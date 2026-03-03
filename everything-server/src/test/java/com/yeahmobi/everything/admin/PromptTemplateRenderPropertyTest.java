package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Prompt template rendering completeness.
 *
 * <p>Feature: yeahmobi-everything, Property 29: Prompt 模板渲染完整性</p>
 */
class PromptTemplateRenderPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 29: Prompt 模板渲染完整性
    void renderPromptTemplate_containsTemplateTextAndUserInput(
            @ForAll("templateParts") String prefix,
            @ForAll("templateParts") String suffix,
            @ForAll("userInputs") String userInput) {
        // **Validates: Requirements 15.9**

        String promptTemplate = prefix + "{{user_input}}" + suffix;

        SkillRepository skillRepo = mock(SkillRepository.class);
        FeedbackRepository feedbackRepo = mock(FeedbackRepository.class);
        CacheService cacheService = mock(CacheService.class);

        AdminServiceImpl service = new AdminServiceImpl(skillRepo, feedbackRepo, cacheService);

        String rendered = service.renderPromptTemplate(promptTemplate, userInput);

        // Rendered result should contain the fixed template text
        assertTrue(rendered.contains(prefix),
                "Rendered result should contain template prefix");
        assertTrue(rendered.contains(suffix),
                "Rendered result should contain template suffix");

        // Rendered result should contain the user input
        assertTrue(rendered.contains(userInput),
                "Rendered result should contain user input");

        // The placeholder should be replaced
        assertFalse(rendered.contains("{{user_input}}"),
                "Rendered result should not contain the placeholder");
    }

    @Provide
    Arbitrary<String> templateParts() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(20).alpha();
    }

    @Provide
    Arbitrary<String> userInputs() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(20).alpha();
    }
}
