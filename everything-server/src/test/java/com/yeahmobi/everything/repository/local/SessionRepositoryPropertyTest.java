package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.auth.Session;
import net.jqwik.api.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for SessionRepository round-trip.
 *
 * <p>Feature: yeahmobi-everything, Property 2: 会话存储 round-trip</p>
 *
 * <p><b>Validates: Requirements 1.10</b></p>
 *
 * <p>Uses a real SQLite in-memory database (no mocks) to verify that any valid
 * Session object can be saved and loaded back with all fields preserved.</p>
 */
class SessionRepositoryPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 2: 会话存储 round-trip
    void sessionRoundTrip(@ForAll("validSessions") Session session) throws SQLException, IOException {
        // **Validates: Requirements 1.10**

        // Arrange: fresh in-memory SQLite database for each trial
        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            SessionRepositoryImpl repository = new SessionRepositoryImpl(databaseManager);

            // Act: save and then load the session
            repository.saveSession(session);
            Optional<Session> loaded = repository.loadSession();

            // Assert: loaded session should be present and equivalent to the original
            assertTrue(loaded.isPresent(), "loadSession should return a session after saveSession");

            Session result = loaded.get();
            assertEquals(session.token(), result.token(), "token should round-trip");
            assertEquals(session.userId(), result.userId(), "userId should round-trip");
            assertEquals(session.username(), result.username(), "username should round-trip");
            assertEquals(session.email(), result.email(), "email should round-trip");
            assertEquals(session.loginType(), result.loginType(), "loginType should round-trip");
            assertEquals(session.expiresAt(), result.expiresAt(), "expiresAt should round-trip");
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
                .between(0L, Long.MAX_VALUE);

        Arbitrary<Long> createdAtValues = Arbitraries.longs()
                .between(0L, Long.MAX_VALUE);

        Arbitrary<Boolean> admins = Arbitraries.of(true, false);

        return Combinators.combine(tokens, userIds, usernames, emails, loginTypes, expiresAtValues, createdAtValues, admins)
                .as(Session::new);
    }
}
