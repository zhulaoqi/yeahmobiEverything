package com.yeahmobi.everything.admin;

/**
 * Represents the result of a Skill integration or creation operation.
 *
 * @param success whether the operation succeeded
 * @param message a human-readable message describing the result
 * @param skill   the created or integrated SkillAdmin, or null on failure
 */
public record SkillIntegrationResult(boolean success, String message, SkillAdmin skill) {}
