package com.yeahmobi.everything.auth;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.LocalDatabaseManager;
import com.yeahmobi.everything.repository.local.SessionRepositoryImpl;
import com.yeahmobi.everything.repository.mysql.UserRepository;
import net.jqwik.api.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for AuthService logout behavior.
 *
 * <p><b>Feature: yeahmobi-everything, Property 3: 退出登录清除会话</b></p>
 *
 * <p><b>Validates: Requirements 1.11</b></p>
 *
 * <p>Uses a real SQLite in-memory database for SessionRepository to verify that
 * calling logout clears the locally stored session. CacheService and
 * FeishuOAuthService are mocked since they are not the focus of this property.</p>
 */
class AuthServiceLogoutPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 3: 退出登录清除会话
    void logoutClearsSession(@ForAll("validSessions") Session session) throws SQLException, IOException {
        // **Validates: Requirements 1.11**

        // Arrange: fresh in-memory SQLite database for each trial
        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            SessionRepositoryImpl sessionRepository = new SessionRepositoryImpl(databaseManager);

            CacheService cacheService = mock(CacheService.class);
            UserRepository userRepository = mock(UserRepository.class);
            FeishuOAuthService feishuOAuthService = mock(FeishuOAuthService.class);

            AuthServiceImpl authService = new AuthServiceImpl(
                    sessionRepository, userRepository, cacheService, feishuOAuthService);

            // Step 1: Save the session and verify it is stored
            sessionRepository.saveSession(session);
            Optional<Session> loaded = sessionRepository.loadSession();
            assertTrue(loaded.isPresent(), "Session should be present after saving");

            // Step 2: Call logout
            authService.logout();

            // Step 3: Verify session is cleared from local storage
            Optional<Session> afterLogout = sessionRepository.loadSession();
            assertTrue(afterLogout.isEmpty(),
                    "loadSession should return Optional.empty() after logout");

            // Step 4: Verify Redis cache removal was called with the session token
            verify(cacheService).removeCachedSession(session.token());
        } finally {
            databaseManager.close();
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<Session> validSessions() {
        Arbitrary<String> tokens = Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(64)
                .alpha()
                .numeric();

        Arbitrary<String> userIds = Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(36)
                .alpha()
                .numeric();

        Arbitrary<String> usernames = Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(50)
                .alpha();

        Arbitrary<String> emails = Combinators.combine(
                Arbitraries.strings().ofMinLength(1).ofMaxLength(20).alpha().numeric(),
                Arbitraries.of("example.com", "test.org", "yeahmobi.com", "corp.io")
        ).as((local, domain) -> local + "@" + domain);

        Arbitrary<String> loginTypes = Arbitraries.of("email", "feishu");

        Arbitrary<Long> expiresAtValues = Arbitraries.longs()
                .between(System.currentTimeMillis(), System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000);

        Arbitrary<Long> createdAtValues = Arbitraries.longs()
                .between(0L, System.currentTimeMillis());

        Arbitrary<Boolean> admins = Arbitraries.of(true, false);

        return Combinators.combine(tokens, userIds, usernames, emails, loginTypes, expiresAtValues, createdAtValues, admins)
                .as(Session::new);
    }
}
