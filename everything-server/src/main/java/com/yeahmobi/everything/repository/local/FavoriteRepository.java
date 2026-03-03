package com.yeahmobi.everything.repository.local;

import java.util.List;

/**
 * Repository interface for managing user favorites in the local SQLite database.
 * <p>
 * Favorites allow users to bookmark Skills for quick access. The data is stored
 * locally in the {@code favorite} table with a composite primary key of
 * (user_id, skill_id).
 * </p>
 */
public interface FavoriteRepository {

    /**
     * Adds a Skill to the user's favorites.
     * If the Skill is already a favorite, this operation is a no-op.
     *
     * @param userId  the user ID
     * @param skillId the skill ID to add as favorite
     */
    void addFavorite(String userId, String skillId);

    /**
     * Removes a Skill from the user's favorites.
     * If the Skill is not a favorite, this operation is a no-op.
     *
     * @param userId  the user ID
     * @param skillId the skill ID to remove from favorites
     */
    void removeFavorite(String userId, String skillId);

    /**
     * Gets all favorite Skill IDs for a user.
     *
     * @param userId the user ID
     * @return a list of favorite skill IDs
     */
    List<String> getFavoriteSkillIds(String userId);

    /**
     * Checks whether a Skill is in the user's favorites.
     *
     * @param userId  the user ID
     * @param skillId the skill ID to check
     * @return true if the Skill is a favorite, false otherwise
     */
    boolean isFavorite(String userId, String skillId);
}
