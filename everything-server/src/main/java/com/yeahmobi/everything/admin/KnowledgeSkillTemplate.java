package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillType;

import java.util.List;

/**
 * Template for creating a knowledge-based (KNOWLEDGE_RAG) Skill via the admin wizard.
 *
 * @param name                   the skill name (required)
 * @param description            the skill description (required)
 * @param category               the skill category (required)
 * @param icon                   the skill icon path or URL
 * @param type                   skill attribution type (GENERAL or INTERNAL)
 * @param promptTemplate         the prompt template for LLM interaction
 * @param knowledgeFileIds       list of knowledge file IDs to bind to the new Skill
 * @param manualKnowledgeContent optional manual knowledge content (may be null or empty)
 * @param executionMode          single or multi-agent execution mode
 */
public record KnowledgeSkillTemplate(
    String name,
    String description,
    String category,
    String icon,
    SkillType type,
    String promptTemplate,
    List<String> knowledgeFileIds,
    String manualKnowledgeContent,
    SkillExecutionMode executionMode
) {}
