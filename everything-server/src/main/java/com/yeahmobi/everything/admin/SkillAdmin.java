package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;

import java.util.List;

/**
 * Represents a Skill with administrative metadata, used in the admin management interface.
 *
 * @param id              the unique skill ID
 * @param name            the skill name
 * @param description     the skill description
 * @param icon            the skill icon path or URL
 * @param category        the skill category (e.g., "翻译", "写作", "开发")
 * @param enabled         whether the skill is enabled
 * @param usageGuide      detailed usage guide
 * @param examples        example inputs (stored as JSON in MySQL)
 * @param i18nJson        localized display metadata in JSON (optional)
 * @param source          skill source (seed/admin/external/importer)
 * @param sourceLang      original language tag (e.g. zh/en)
 * @param qualityTier     quality tier (basic/verified)
 * @param toolIds         enabled tool IDs for this skill (optional)
 * @param toolGroups      enabled tool groups for this skill (optional)
 * @param contextPolicy   context policy hint for agent execution
 * @param type            skill attribution type (GENERAL or INTERNAL)
 * @param kind            skill driving type (PROMPT_ONLY or KNOWLEDGE_RAG)
 * @param promptTemplate  the prompt template for LLM interaction
 * @param executionMode   single or multi-agent execution mode
 * @param createdAt       creation timestamp in epoch millis
 */
public record SkillAdmin(
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
    SkillExecutionMode executionMode,
    long createdAt
) {
    /**
     * Backward-compatible constructor (before tool metadata fields were introduced).
     */
    public SkillAdmin(
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
            SkillExecutionMode executionMode,
            long createdAt
    ) {
        this(
                id, name, description, icon, category, enabled,
                usageGuide, examples, i18nJson, source, sourceLang, qualityTier,
                List.of(), List.of(), "default",
                type, kind, promptTemplate, executionMode, createdAt
        );
    }
}
