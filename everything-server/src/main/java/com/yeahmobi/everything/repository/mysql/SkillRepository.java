package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.admin.SkillAdmin;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing Skill configurations in the MySQL database.
 * <p>
 * Provides CRUD operations for {@link SkillAdmin} records stored in the
 * {@code skill} table. Used by the admin module for Skill management
 * and by the Skill service for fetching Skill data.
 * </p>
 */
public interface SkillRepository {

    /**
     * Saves a new Skill to the database.
     *
     * @param skill the skill to save
     */
    void saveSkill(SkillAdmin skill);

    /**
     * Gets a Skill by its unique ID.
     *
     * @param skillId the skill ID
     * @return an Optional containing the skill if found, or empty otherwise
     */
    Optional<SkillAdmin> getSkill(String skillId);

    /**
     * Gets all Skills from the database.
     *
     * @return a list of all skills
     */
    List<SkillAdmin> getAllSkills();

    /**
     * Updates an existing Skill in the database.
     *
     * @param skill the skill with updated fields
     */
    void updateSkill(SkillAdmin skill);

    /**
     * Deletes a Skill by its unique ID.
     *
     * @param skillId the skill ID to delete
     */
    void deleteSkill(String skillId);
}
