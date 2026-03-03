package com.yeahmobi.everything.knowledge;

import com.yeahmobi.everything.auth.Session;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.skill.Skill;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory CacheService for testing knowledge base operations.
 */
class NoOpCacheService implements CacheService {

    private final Map<String, String> knowledgeCache = new ConcurrentHashMap<>();

    @Override
    public void cacheSession(String token, Session session, long ttlSeconds) {}

    @Override
    public Optional<Session> getCachedSession(String token) { return Optional.empty(); }

    @Override
    public void removeCachedSession(String token) {}

    @Override
    public void cacheSkillList(List<Skill> skills, long ttlSeconds) {}

    @Override
    public Optional<List<Skill>> getCachedSkillList() { return Optional.empty(); }

    @Override
    public void invalidateSkillCache() {}

    @Override
    public void cacheKnowledgeText(String skillId, String text, long ttlSeconds) {
        knowledgeCache.put(skillId, text);
    }

    @Override
    public Optional<String> getCachedKnowledgeText(String skillId) {
        return Optional.ofNullable(knowledgeCache.get(skillId));
    }

    @Override
    public void invalidateKnowledgeCache(String skillId) {
        knowledgeCache.remove(skillId);
    }
}
