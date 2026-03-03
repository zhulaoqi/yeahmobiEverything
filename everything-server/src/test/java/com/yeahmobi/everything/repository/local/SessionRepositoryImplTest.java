package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.auth.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SessionRepositoryImpl} using an in-memory SQLite database.
 */
class SessionRepositoryImplTest {

    private LocalDatabaseManager databaseManager;
    private SessionRepositoryImpl repository;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        databaseManager = new LocalDatabaseManager(":memory:");
        databaseManager.initialize();
        repository = new SessionRepositoryImpl(databaseManager);
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Test
    void saveAndLoadSession_emailLogin() {
        Session session = new Session(
                "token-abc-123",
                "user-001",
                "Alice",
                "alice@example.com",
                "email",
                System.currentTimeMillis() + 86400000L,
                1700000000000L,
                false
        );

        repository.saveSession(session);
        Optional<Session> loaded = repository.loadSession();

        assertTrue(loaded.isPresent(), "Session should be loaded after save");
        Session result = loaded.get();
        assertEquals(session.token(), result.token());
        assertEquals(session.userId(), result.userId());
        assertEquals(session.username(), result.username());
        assertEquals(session.email(), result.email());
        assertEquals(session.loginType(), result.loginType());
        assertEquals(session.expiresAt(), result.expiresAt());
    }

    @Test
    void saveAndLoadSession_feishuLogin() {
        Session session = new Session(
                "feishu-token-xyz",
                "user-002",
                "Bob",
                "bob@company.com",
                "feishu",
                System.currentTimeMillis() + 604800000L,
                1700000000000L,
                false
        );

        repository.saveSession(session);
        Optional<Session> loaded = repository.loadSession();

        assertTrue(loaded.isPresent());
        Session result = loaded.get();
        assertEquals("feishu", result.loginType());
        assertEquals("feishu-token-xyz", result.token());
        assertEquals("Bob", result.username());
    }

    @Test
    void loadSession_returnsEmptyWhenNoSession() {
        Optional<Session> loaded = repository.loadSession();
        assertTrue(loaded.isEmpty(), "Should return empty when no session exists");
    }

    @Test
    void saveSession_replacesExistingSession() {
        Session first = new Session("token-1", "user-1", "First", "first@test.com", "email", 1000L, 111L, false);
        Session second = new Session("token-2", "user-2", "Second", "second@test.com", "feishu", 2000L, 222L, false);

        repository.saveSession(first);
        repository.saveSession(second);

        Optional<Session> loaded = repository.loadSession();
        assertTrue(loaded.isPresent());
        assertEquals("token-2", loaded.get().token());
        assertEquals("user-2", loaded.get().userId());
        assertEquals("Second", loaded.get().username());
    }

    @Test
    void clearSession_removesAllSessions() {
        Session session = new Session("token-1", "user-1", "User", "user@test.com", "email", 5000L, 333L, false);
        repository.saveSession(session);

        // Verify session exists
        assertTrue(repository.loadSession().isPresent());

        // Clear and verify
        repository.clearSession();
        assertTrue(repository.loadSession().isEmpty(), "Session should be cleared");
    }

    @Test
    void clearSession_onEmptyTable_doesNotThrow() {
        assertDoesNotThrow(() -> repository.clearSession());
        assertTrue(repository.loadSession().isEmpty());
    }

    @Test
    void saveSession_withNullEmail() {
        Session session = new Session("token-1", "user-1", "User", null, "feishu", 5000L, 444L, false);
        repository.saveSession(session);

        Optional<Session> loaded = repository.loadSession();
        assertTrue(loaded.isPresent());
        assertNull(loaded.get().email());
    }

    @Test
    void saveSession_preservesExpiresAtPrecision() {
        long expiresAt = 1700000000123L;
        Session session = new Session("token-1", "user-1", "User", "u@t.com", "email", expiresAt, 555L, false);
        repository.saveSession(session);

        Optional<Session> loaded = repository.loadSession();
        assertTrue(loaded.isPresent());
        assertEquals(expiresAt, loaded.get().expiresAt());
    }

    @Test
    void multipleSaveClearCycles() {
        // Save, clear, save again — should work correctly
        Session s1 = new Session("t1", "u1", "User1", "u1@t.com", "email", 1000L, 666L, false);
        repository.saveSession(s1);
        assertTrue(repository.loadSession().isPresent());

        repository.clearSession();
        assertTrue(repository.loadSession().isEmpty());

        Session s2 = new Session("t2", "u2", "User2", "u2@t.com", "feishu", 2000L, 777L, false);
        repository.saveSession(s2);
        Optional<Session> loaded = repository.loadSession();
        assertTrue(loaded.isPresent());
        assertEquals("t2", loaded.get().token());
    }
}
