package com.yeahmobi.everything.knowledge;

/**
 * Represents the binding between a Skill and a knowledge base file.
 *
 * @param skillId          the Skill ID
 * @param knowledgeFileId  the knowledge file ID
 * @param boundAt          binding timestamp as epoch milliseconds
 */
public record SkillKnowledgeBinding(
    String skillId,
    String knowledgeFileId,
    long boundAt
) {}
