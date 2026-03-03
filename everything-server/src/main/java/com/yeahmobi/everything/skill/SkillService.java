package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.common.NetworkException;

import java.util.List;

/**
 * Service interface for Skill management operations.
 * <p>
 * Provides methods for fetching, searching, filtering, and managing Skills.
 * The {@link #fetchSkills()} method uses Redis caching to improve performance.
 * </p>
 */
public interface SkillService {

    /**
     * Fetches all available Skills from the server.
     * <p>
     * Implementation should check Redis cache first. If the cache is empty,
     * query the SkillRepository (MySQL) and cache the result.
     * </p>
     *
     * @return a list of all available skills
     * @throws NetworkException if a network error occurs during fetching
     */
    List<Skill> fetchSkills() throws NetworkException;

    /**
     * Searches Skills by keyword (case-insensitive match on name and description).
     *
     * @param keyword   the search keyword
     * @param allSkills the list of skills to search within
     * @return a list of skills matching the keyword
     */
    List<Skill> searchSkills(String keyword, List<Skill> allSkills);

    /**
     * Filters Skills by category (exact match on category field).
     *
     * @param category  the category to filter by
     * @param allSkills the list of skills to filter
     * @return a list of skills in the specified category
     */
    List<Skill> filterByCategory(String category, List<Skill> allSkills);

    /**
     * Filters Skills by type (GENERAL or INTERNAL).
     * <p>
     * When type is null, returns all skills.
     * </p>
     *
     * @param type      the skill type to filter by, or null for all
     * @param allSkills the list of skills to filter
     * @return a list of skills of the specified type
     */
    List<Skill> filterByType(SkillType type, List<Skill> allSkills);

    /**
     * Gets the user's favorite Skills.
     *
     * @param userId the user ID
     * @return a list of favorite skills
     */
    List<Skill> getFavorites(String userId);

    /**
     * Toggles the favorite status of a Skill for a user.
     *
     * @param userId  the user ID
     * @param skillId the skill ID
     */
    void toggleFavorite(String userId, String skillId);

    /**
     * Checks if a Skill is favorited by the user.
     *
     * @param userId  the user ID
     * @param skillId the skill ID
     * @return true if favorited, false otherwise
     */
    boolean isFavorite(String userId, String skillId);

    /**
     * Gets all Skills that have chat history for the user.
     *
     * @param userId the user ID
     * @return a list of used skills
     */
    List<Skill> getUsedSkills(String userId);

    /**
     * Gets the user's recently used Skills, ordered by most recent first.
     *
     * @param userId the user ID
     * @param limit  the maximum number of skills to return
     * @return a list of recently used skills
     */
    List<Skill> getRecentlyUsed(String userId, int limit);

    /**
     * Records that a user has used a Skill.
     *
     * @param userId  the user ID
     * @param skillId the skill ID
     */
    void recordUsage(String userId, String skillId);

    /**
     * Gets the usage count of a Skill for a user.
     *
     * @param userId  the user ID
     * @param skillId the skill ID
     * @return usage count
     */
    int getUsageCount(String userId, String skillId);

    /**
     * Gets the predefined default Skills (翻译助手、文案撰写、代码助手、数据分析).
     *
     * @return a list of default skills
     */
    List<Skill> getDefaultSkills();
}
