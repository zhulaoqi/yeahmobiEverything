package com.yeahmobi.everything.repository;

import com.google.gson.Gson;
import com.yeahmobi.everything.auth.Session;
import com.yeahmobi.everything.repository.cache.CacheServiceImpl;
import com.yeahmobi.everything.repository.cache.RedisManager;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Redis session cache round-trip.
 * Uses a HashMap-based mock to simulate Redis get/set/del behavior
 * since no Redis server is available in the test environment.
 */
class CacheServiceTest {

    /**
     * Creates a CacheServiceImpl backed by a HashMap that simulates Redis behavior.
     * The mock Jedis stores values in the map on set() and retrieves them on get().
     */
    private CacheServiceImpl createCacheServiceWithMapBackedRedis(Map<String, String> store) {
        RedisManager redisManager = mock(RedisManager.class);
        Jedis jedis = mock(Jedis.class);
        when(redisManager.getJedis()).thenReturn(jedis);

        // Simulate Redis SET with TTL: store the value in the HashMap
        when(jedis.set(anyString(), anyString(), any(SetParams.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    String value = invocation.getArgument(1);
                    store.put(key, value);
                    return "OK";
                });

        // Simulate Redis GET: retrieve the value from the HashMap
        when(jedis.get(anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    return store.get(key);
                });

        // Simulate Redis DEL: remove the value from the HashMap
        when(jedis.del(anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    return store.remove(key) != null ? 1L : 0L;
                });

        return new CacheServiceImpl(redisManager, new Gson());
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 35: Redis 会话缓存 round-trip
    void redisSessionCacheRoundTrip(@ForAll("validSessions") Session session,
                                     @ForAll("validTokens") String token) {
        // **Validates: Requirements 16.5**
        Map<String, String> store = new HashMap<>();
        CacheServiceImpl cacheService = createCacheServiceWithMapBackedRedis(store);

        // Cache the session
        cacheService.cacheSession(token, session, 604800);

        // Retrieve the cached session
        Optional<Session> retrieved = cacheService.getCachedSession(token);

        // Assert the retrieved session equals the original
        assertTrue(retrieved.isPresent(), "Cached session should be retrievable");
        assertEquals(session, retrieved.get(), "Retrieved session should equal the original");
    }

    @Provide
    Arbitrary<Session> validSessions() {
        Arbitrary<String> tokens = validTokens();
        Arbitrary<String> userIds = nonEmptyAlphanumeric();
        Arbitrary<String> usernames = nonEmptyStrings();
        Arbitrary<String> emails = validEmails();
        Arbitrary<String> loginTypes = Arbitraries.of("email", "feishu");
        Arbitrary<Long> expiresAtValues = Arbitraries.longs().greaterOrEqual(1L);
        Arbitrary<Long> createdAtValues = Arbitraries.longs().between(0L, System.currentTimeMillis());

        Arbitrary<Boolean> admins = Arbitraries.of(true, false);

        return Combinators.combine(tokens, userIds, usernames, emails, loginTypes, expiresAtValues, createdAtValues, admins)
                .as(Session::new);
    }

    @Provide
    Arbitrary<String> validTokens() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(64)
                .alpha()
                .numeric();
    }

    private Arbitrary<String> nonEmptyAlphanumeric() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(32)
                .alpha()
                .numeric();
    }

    private Arbitrary<String> nonEmptyStrings() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(50)
                .alpha();
    }

    private Arbitrary<String> validEmails() {
        Arbitrary<String> localParts = Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(20)
                .alpha()
                .numeric();
        Arbitrary<String> domains = Arbitraries.of("example.com", "test.org", "corp.io", "yeahmobi.com", "mail.cn");

        return Combinators.combine(localParts, domains)
                .as((local, domain) -> local + "@" + domain);
    }
}
