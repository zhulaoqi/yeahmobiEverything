package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;
import net.jqwik.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for SkillCommandParser round-trip.
 *
 * <p>Feature: yeahmobi-everything, Property 11: Skill 集成命令解析 round-trip</p>
 */
class SkillCommandParserPropertyTest {

    private final SkillCommandParser parser = new SkillCommandParser();

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 11: Skill 集成命令解析 round-trip
    void commandRoundTrip_preservesFields(
            @ForAll("skillNames") String name,
            @ForAll("descriptions") String desc,
            @ForAll("categories") String category,
            @ForAll("icons") String icon,
            @ForAll("skillTypes") SkillType type,
            @ForAll("skillKinds") SkillKind kind,
            @ForAll("skillExecs") SkillExecutionMode exec,
            @ForAll("prompts") String prompt) {
        // **Validates: Requirements 5.3**

        // Build a SkillAdmin with known fields
        SkillAdmin original = new SkillAdmin(
                "test-id", name, desc, icon, category,
                true,
                "",
                List.of(),
                null,
                "test",
                "en",
                "basic",
                type, kind, prompt, exec, 1000L
        );

        // Serialize to command string
        String command = parser.toCommand(original);

        // Parse back
        SkillAdmin parsed = parser.parse(command);

        // Verify all serialized fields are preserved
        assertEquals(name, parsed.name(), "name should round-trip");
        assertEquals(desc, parsed.description(), "description should round-trip");
        assertEquals(category, parsed.category(), "category should round-trip");
        assertEquals(icon, parsed.icon(), "icon should round-trip");
        assertEquals(type, parsed.type(), "type should round-trip");
        assertEquals(kind, parsed.kind(), "kind should round-trip");
        assertEquals(exec, parsed.executionMode(), "executionMode should round-trip");
        assertEquals(prompt, parsed.promptTemplate(), "promptTemplate should round-trip");
        assertTrue(parsed.enabled(), "newly parsed skill should be enabled");
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> skillNames() {
        return Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(20)
                .withChars('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                        'A', 'B', 'C', 'D', 'E', 'F',
                        '翻', '译', '助', '手', '代', '码', '写', '作');
    }

    @Provide
    Arbitrary<String> descriptions() {
        return Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(30)
                .withChars('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                        '帮', '助', '翻', '译', '文', '本', '编', '写');
    }

    @Provide
    Arbitrary<String> categories() {
        return Arbitraries.of("翻译", "写作", "开发", "数据", "其他");
    }

    @Provide
    Arbitrary<String> icons() {
        return Arbitraries.of("icon.png", "translate.png", "code.png", "data.png", "default.png");
    }

    @Provide
    Arbitrary<SkillExecutionMode> skillExecs() {
        return Arbitraries.of(SkillExecutionMode.SINGLE, SkillExecutionMode.MULTI);
    }

    @Provide
    Arbitrary<SkillType> skillTypes() {
        return Arbitraries.of(SkillType.GENERAL, SkillType.INTERNAL);
    }

    @Provide
    Arbitrary<SkillKind> skillKinds() {
        return Arbitraries.of(SkillKind.PROMPT_ONLY, SkillKind.KNOWLEDGE_RAG);
    }

    @Provide
    Arbitrary<String> prompts() {
        return Arbitraries.strings()
                .ofMinLength(0).ofMaxLength(50)
                .withChars('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                        ' ', '.', ',', ':', '你', '是', '一', '个', '专', '业');
    }
}
