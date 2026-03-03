package com.yeahmobi.everything.repository.cache;

import com.yeahmobi.everything.auth.Session;
import com.yeahmobi.everything.skill.Skill;

import java.util.List;
import java.util.Optional;

/**
 * Cache service interface for Redis-based caching.
 * Provides caching for user sessions, Skill lists, and knowledge base text.
 */
public interface CacheService {

    /**
     * Caches a user session.
     *
     * @param token      the session token (used as cache key)
     * @param session    the session to cache
     * @param ttlSeconds time-to-live in seconds
     */
    void cacheSession(String token, Session session, long ttlSeconds);

    /**
     * Gets a cached session by token.
     *
     * @param token the session token
     * @return the cached session, or empty if not found or cache unavailable
     */
    Optional<Session> getCachedSession(String token);

    /**
     * Removes a cached session.
     *
     * @param token the session token
     */
    void removeCachedSession(String token);

    /**
     * Caches the full Skill list.
     *
     * @param skills     the list of skills to cache
     * @param ttlSeconds time-to-live in seconds
     */
    void cacheSkillList(List<Skill> skills, long ttlSeconds);

    /**
     * Gets the cached Skill list.
     *
     * @return the cached skill list, or empty if not found or cache unavailable
     */
    Optional<List<Skill>> getCachedSkillList();

    /**
     * Clears the Skill list cache.
     */
    void invalidateSkillCache();

    /**
     * Caches merged knowledge text for a Skill.
     *
     * @param skillId    the skill ID
     * @param text       the merged knowledge text
     * @param ttlSeconds time-to-live in seconds
     */
    void cacheKnowledgeText(String skillId, String text, long ttlSeconds);

    /**
     * Gets cached knowledge text for a Skill.
     *
     * @param skillId the skill ID
     * @return the cached knowledge text, or empty if not found or cache unavailable
     */
    Optional<String> getCachedKnowledgeText(String skillId);

    /**
     * Clears the knowledge cache for a specific Skill.
     *
     * @param skillId the skill ID
     */
    void invalidateKnowledgeCache(String skillId);
}
