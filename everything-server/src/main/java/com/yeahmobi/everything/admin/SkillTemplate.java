package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillType;

/**
 * Template for creating a general (PROMPT_ONLY) Skill via the admin form.
 *
 * @param name            the skill name (required)
 * @param description     the skill description (required)
 * @param category        the skill category (required)
 * @param icon            the skill icon path or URL
 * @param type            skill attribution type (GENERAL or INTERNAL)
 * @param promptTemplate  the prompt template for LLM interaction
 * @param executionMode   single or multi-agent execution mode
 */
public record SkillTemplate(
    String name,
    String description,
    String category,
    String icon,
    SkillType type,
    String promptTemplate,
    SkillExecutionMode executionMode
) {}
