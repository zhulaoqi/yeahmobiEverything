package com.yeahmobi.everything.personalskill;

/**
 * Personal skill created by a user, visible only to them until approved.
 *
 * @param id            unique skill ID
 * @param userId        owner user ID
 * @param name          skill name
 * @param description   skill description
 * @param category      skill category
 * @param promptTemplate prompt template for the skill
 * @param status        review status
 * @param reviewerNote  admin review note (optional)
 * @param createdAt     created timestamp in epoch millis
 * @param updatedAt     updated timestamp in epoch millis
 */
public record PersonalSkill(
    String id,
    String userId,
    String name,
    String description,
    String category,
    String promptTemplate,
    PersonalSkillStatus status,
    String reviewerNote,
    long createdAt,
    long updatedAt
) {}
