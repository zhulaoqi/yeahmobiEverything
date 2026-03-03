package com.yeahmobi.everything.skill;

import java.util.List;

/**
 * Canonical skill manifest parsed from SKILL.md frontmatter + body.
 * <p>
 * This internal model is used by importers to normalize heterogeneous skill
 * packages before persisting to MySQL.
 * </p>
 */
public record SkillManifest(
        String name,
        String description,
        String category,
        String sourceLang,
        SkillExecutionMode executionMode,
        List<String> examples,
        String usageGuide,
        String promptTemplate,
        List<String> toolIds,
        List<String> toolGroups,
        String contextPolicy
) {}

