package com.yeahmobi.everything.skill;

/**
 * Skill driving type.
 */
public enum SkillKind {
    /** Prompt-only Skill: pure Prompt driven */
    PROMPT_ONLY,
    /** Knowledge RAG Skill: Prompt + Knowledge base RAG */
    KNOWLEDGE_RAG
}
