package com.yeahmobi.everything.personalskill;

/**
 * Result of a personal skill operation.
 *
 * @param success whether the operation succeeded
 * @param message user-friendly message
 * @param skill   optional skill payload
 */
public record PersonalSkillResult(boolean success, String message, PersonalSkill skill) {}
