package com.yeahmobi.everything;

import com.yeahmobi.everything.auth.Session;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.skill.Skill;

import java.util.List;
import java.util.Optional;

/**
 * A no-op implementation of {@link CacheService} used when Redis is unavailable.
 * <p>
 * All cache operations are silently ignored, and all reads return empty results.
 * This ensures the application degrades gracefully without caching rather than
 * failing when Redis is not connected.
 * </p>
 */
class NoOpCacheService implements CacheService {

    @Override
    public void cacheSession(String token, Session session, long ttlSeconds) {
        // No-op: Redis unavailable
    }

    @Override
    public Optional<Session> getCachedSession(String token) {
        return Optional.empty();
    }

    @Override
    public void removeCachedSession(String token) {
        // No-op: Redis unavailable
    }

    @Override
    public void cacheSkillList(List<Skill> skills, long ttlSeconds) {
        // No-op: Redis unavailable
    }

    @Override
    public Optional<List<Skill>> getCachedSkillList() {
        return Optional.empty();
    }

    @Override
    public void invalidateSkillCache() {
        // No-op: Redis unavailable
    }

    @Override
    public void cacheKnowledgeText(String skillId, String text, long ttlSeconds) {
        // No-op: Redis unavailable
    }

    @Override
    public Optional<String> getCachedKnowledgeText(String skillId) {
        return Optional.empty();
    }

    @Override
    public void invalidateKnowledgeCache(String skillId) {
        // No-op: Redis unavailable
    }
}
