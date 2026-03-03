package com.yeahmobi.everything.skill;

import java.util.List;

/**
 * Represents a Skill in the system.
 *
 * @param id              the unique skill ID
 * @param name            the skill name
 * @param description     the skill description
 * @param icon            the skill icon path or URL
 * @param category        the skill category (e.g., "翻译", "写作", "开发")
 * @param enabled         whether the skill is enabled
 * @param usageGuide      detailed usage guide
 * @param examples        example inputs for the skill
 * @param i18nJson        localized display metadata in JSON (optional)
 * @param source          skill source (seed/admin/external/importer)
 * @param sourceLang      original language tag (e.g. zh/en)
 * @param qualityTier     quality tier (basic/verified)
 * @param toolIds         explicit tool IDs bound to this skill
 * @param toolGroups      tool groups bound to this skill
 * @param contextPolicy   progressive disclosure policy (minimal/standard/advanced)
 * @param type            skill attribution type (GENERAL or INTERNAL)
 * @param kind            skill driving type (PROMPT_ONLY or KNOWLEDGE_RAG)
 * @param promptTemplate  the prompt template for LLM interaction
 * @param executionMode   single or multi-agent execution mode
 */
public record Skill(
    String id,
    String name,
    String description,
    String icon,
    String category,
    boolean enabled,
    String usageGuide,
    List<String> examples,
    String i18nJson,
    String source,
    String sourceLang,
    String qualityTier,
    List<String> toolIds,
    List<String> toolGroups,
    String contextPolicy,
    SkillType type,
    SkillKind kind,
    String promptTemplate,
    SkillExecutionMode executionMode
) {
    /**
     * Backward-compatible constructor for older call sites.
     */
    public Skill(
            String id,
            String name,
            String description,
            String icon,
            String category,
            boolean enabled,
            String usageGuide,
            List<String> examples,
            String i18nJson,
            String source,
            String sourceLang,
            String qualityTier,
            SkillType type,
            SkillKind kind,
            String promptTemplate,
            SkillExecutionMode executionMode
    ) {
        this(
                id, name, description, icon, category, enabled,
                usageGuide, examples, i18nJson, source, sourceLang, qualityTier,
                List.of(), List.of(), "standard",
                type, kind, promptTemplate, executionMode
        );
    }
}
