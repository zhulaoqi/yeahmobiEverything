package com.yeahmobi.everything.repository.local;

import java.util.List;

/**
 * Repository interface for tracking Skill usage in the local SQLite database.
 * <p>
 * Records each time a user uses a Skill, enabling the "recently used" feature.
 * Data is stored in the {@code skill_usage} table.
 * </p>
 */
public interface UsageRepository {

    /**
     * Records that a user has used a Skill.
     * Each call creates a new usage record with the current timestamp.
     *
     * @param userId  the user ID
     * @param skillId the skill ID that was used
     */
    void recordUsage(String userId, String skillId);

    /**
     * Gets the most recently used Skill IDs for a user, ordered by most recent first.
     * <p>
     * Returns distinct Skill IDs — if a Skill was used multiple times, only
     * the most recent usage is considered for ordering, and the Skill appears
     * only once in the result.
     * </p>
     *
     * @param userId the user ID
     * @param limit  the maximum number of skill IDs to return
     * @return a list of recently used skill IDs, ordered by most recent first
     */
    List<String> getRecentSkillIds(String userId, int limit);

    /**
     * Gets the total usage count of a Skill for a user.
     *
     * @param userId  the user ID
     * @param skillId the skill ID
     * @return total usage count
     */
    int getUsageCount(String userId, String skillId);
}
