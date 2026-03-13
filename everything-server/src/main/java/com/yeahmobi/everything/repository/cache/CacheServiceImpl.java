package com.yeahmobi.everything.repository.cache;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yeahmobi.everything.auth.Session;
import com.yeahmobi.everything.skill.Skill;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

/**
 * Redis-based implementation of {@link CacheService}.
 * <p>
 * Uses Gson for JSON serialization/deserialization and RedisManager for Redis operations.
 * Handles Redis connection failures gracefully by logging warnings and returning empty Optionals.
 * Redis unavailability does NOT crash the application.
 * </p>
 *
 * <h3>Redis key patterns:</h3>
 * <ul>
 *   <li>{@code session:{token}} → JSON(Session) TTL=7d</li>
 *   <li>{@code skills:all} → JSON(List&lt;Skill&gt;) TTL=10m</li>
 *   <li>{@code knowledge:{skillId}} → String(mergedText) TTL=30m</li>
 * </ul>
 */
public class CacheServiceImpl implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheServiceImpl.class);

    static final String SESSION_KEY_PREFIX = "session:";
    static final String SKILLS_ALL_KEY = "skills:all";
    static final String KNOWLEDGE_KEY_PREFIX = "knowledge:";

    private final RedisManager redisManager;
    private final Gson gson;

    /**
     * Creates a CacheServiceImpl with the given RedisManager.
     *
     * @param redisManager the Redis connection manager
     */
    public CacheServiceImpl(RedisManager redisManager) {
        this.redisManager = redisManager;
        this.gson = new Gson();
    }

    /**
     * Creates a CacheServiceImpl with the given RedisManager and Gson instance.
     * Useful for testing with custom Gson configurations.
     *
     * @param redisManager the Redis connection manager
     * @param gson         the Gson instance for JSON serialization
     */
    public CacheServiceImpl(RedisManager redisManager, Gson gson) {
        this.redisManager = redisManager;
        this.gson = gson;
    }

    @Override
    public void cacheSession(String token, Session session, long ttlSeconds) {
        try (Jedis jedis = redisManager.getJedis()) {
            String key = SESSION_KEY_PREFIX + token;
            String json = gson.toJson(session);
            jedis.set(key, json, SetParams.setParams().ex(ttlSeconds));
        } catch (Exception e) {
            log.warn("Failed to cache session for token: {}", token, e);
        }
    }

    @Override
    public Optional<Session> getCachedSession(String token) {
        try (Jedis jedis = redisManager.getJedis()) {
            String key = SESSION_KEY_PREFIX + token;
            String json = jedis.get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(gson.fromJson(json, Session.class));
        } catch (Exception e) {
            log.warn("Failed to get cached session for token: {}", token, e);
            return Optional.empty();
        }
    }

    @Override
    public void removeCachedSession(String token) {
        try (Jedis jedis = redisManager.getJedis()) {
            String key = SESSION_KEY_PREFIX + token;
            jedis.del(key);
        } catch (Exception e) {
            log.warn("Failed to remove cached session for token: {}", token, e);
        }
    }

    @Override
    public void cacheSkillList(List<Skill> skills, long ttlSeconds) {
        try (Jedis jedis = redisManager.getJedis()) {
            String json = gson.toJson(skills);
            jedis.set(SKILLS_ALL_KEY, json, SetParams.setParams().ex(ttlSeconds));
        } catch (Exception e) {
            log.warn("Failed to cache skill list", e);
        }
    }

    @Override
    public Optional<List<Skill>> getCachedSkillList() {
        try (Jedis jedis = redisManager.getJedis()) {
            String json = jedis.get(SKILLS_ALL_KEY);
            if (json == null) {
                return Optional.empty();
            }
            Type listType = new TypeToken<List<Skill>>() {}.getType();
            List<Skill> skills = gson.fromJson(json, listType);
            return Optional.of(skills);
        } catch (Exception e) {
            log.warn("Failed to get cached skill list", e);
            return Optional.empty();
        }
    }

    @Override
    public void invalidateSkillCache() {
        try (Jedis jedis = redisManager.getJedis()) {
            jedis.del(SKILLS_ALL_KEY);
        } catch (Exception e) {
            log.warn("Failed to invalidate skill cache", e);
        }
    }

    @Override
    public void cacheKnowledgeText(String skillId, String text, long ttlSeconds) {
        try (Jedis jedis = redisManager.getJedis()) {
            String key = KNOWLEDGE_KEY_PREFIX + skillId;
            jedis.set(key, text, SetParams.setParams().ex(ttlSeconds));
        } catch (Exception e) {
            log.warn("Failed to cache knowledge text for skill: {}", skillId, e);
        }
    }

    @Override
    public Optional<String> getCachedKnowledgeText(String skillId) {
        try (Jedis jedis = redisManager.getJedis()) {
            String key = KNOWLEDGE_KEY_PREFIX + skillId;
            String text = jedis.get(key);
            if (text == null) {
                return Optional.empty();
            }
            return Optional.of(text);
        } catch (Exception e) {
            log.warn("Failed to get cached knowledge text for skill: {}", skillId, e);
            return Optional.empty();
        }
    }

    @Override
    public void invalidateKnowledgeCache(String skillId) {
        try (Jedis jedis = redisManager.getJedis()) {
            String key = KNOWLEDGE_KEY_PREFIX + skillId;
            jedis.del(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate knowledge cache for skill: {}", skillId, e);
        }
    }
}
