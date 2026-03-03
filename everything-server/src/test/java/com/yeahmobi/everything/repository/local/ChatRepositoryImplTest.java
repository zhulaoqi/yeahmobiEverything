package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.chat.ChatMessage;
import com.yeahmobi.everything.chat.ChatSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChatRepositoryImpl} using an in-memory SQLite database.
 */
class ChatRepositoryImplTest {

    private LocalDatabaseManager databaseManager;
    private ChatRepositoryImpl repository;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        databaseManager = new LocalDatabaseManager(":memory:");
        databaseManager.initialize();
        repository = new ChatRepositoryImpl(databaseManager);
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    // --- saveMessage and getHistory ---

    @Test
    void saveMessageAndGetHistory() {
        ChatMessage msg = new ChatMessage("m1", "s1", "skill-1", "user", "Hello", 1000L);
        repository.saveMessage(msg, "user-1", "Translation");

        List<ChatMessage> history = repository.getHistory("s1");

        assertEquals(1, history.size());
        ChatMessage retrieved = history.get(0);
        assertEquals("m1", retrieved.id());
        assertEquals("s1", retrieved.sessionId());
        assertEquals("skill-1", retrieved.skillId());
        assertEquals("user", retrieved.role());
        assertEquals("Hello", retrieved.content());
        assertEquals(1000L, retrieved.timestamp());
    }

    @Test
    void getHistoryReturnsMessagesInChronologicalOrder() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "First", 3000L), "user-1", "Skill");
        repository.saveMessage(new ChatMessage("m2", "s1", "skill-1", "assistant", "Second", 1000L), "user-1", "Skill");
        repository.saveMessage(new ChatMessage("m3", "s1", "skill-1", "user", "Third", 2000L), "user-1", "Skill");

        List<ChatMessage> history = repository.getHistory("s1");

        assertEquals(3, history.size());
        assertEquals("m2", history.get(0).id()); // timestamp 1000
        assertEquals("m3", history.get(1).id()); // timestamp 2000
        assertEquals("m1", history.get(2).id()); // timestamp 3000
    }

    @Test
    void getHistoryReturnsEmptyForUnknownSession() {
        List<ChatMessage> history = repository.getHistory("nonexistent");
        assertTrue(history.isEmpty());
    }

    @Test
    void getHistoryIsolatedBySession() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Session 1", 1000L), "user-1", "Skill");
        repository.saveMessage(new ChatMessage("m2", "s2", "skill-1", "user", "Session 2", 2000L), "user-1", "Skill");

        List<ChatMessage> history1 = repository.getHistory("s1");
        List<ChatMessage> history2 = repository.getHistory("s2");

        assertEquals(1, history1.size());
        assertEquals("m1", history1.get(0).id());
        assertEquals(1, history2.size());
        assertEquals("m2", history2.get(0).id());
    }

    // --- saveMessage upserts session ---

    @Test
    void saveMessageCreatesSession() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Hello", 1000L), "user-1", "Translation");

        List<ChatSession> sessions = repository.getAllSessions("user-1");

        assertEquals(1, sessions.size());
        ChatSession session = sessions.get(0);
        assertEquals("s1", session.id());
        assertEquals("skill-1", session.skillId());
        assertEquals("Translation", session.skillName());
        assertEquals("Hello", session.lastMessage());
        assertEquals(1000L, session.lastTimestamp());
    }

    @Test
    void saveMessageUpdatesSessionLastMessageAndTimestamp() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Hello", 1000L), "user-1", "Translation");
        repository.saveMessage(new ChatMessage("m2", "s1", "skill-1", "assistant", "Hi there!", 2000L), "user-1", "Translation");

        List<ChatSession> sessions = repository.getAllSessions("user-1");

        assertEquals(1, sessions.size());
        assertEquals("Hi there!", sessions.get(0).lastMessage());
        assertEquals(2000L, sessions.get(0).lastTimestamp());
    }

    // --- clearHistory ---

    @Test
    void clearHistoryRemovesAllMessagesForSession() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Hello", 1000L), "user-1", "Skill");
        repository.saveMessage(new ChatMessage("m2", "s1", "skill-1", "assistant", "Hi", 2000L), "user-1", "Skill");

        repository.clearHistory("s1");

        assertTrue(repository.getHistory("s1").isEmpty());
    }

    @Test
    void clearHistoryDoesNotAffectOtherSessions() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Hello", 1000L), "user-1", "Skill");
        repository.saveMessage(new ChatMessage("m2", "s2", "skill-1", "user", "World", 2000L), "user-1", "Skill");

        repository.clearHistory("s1");

        assertTrue(repository.getHistory("s1").isEmpty());
        assertEquals(1, repository.getHistory("s2").size());
    }

    @Test
    void clearHistoryOnEmptySessionIsNoOp() {
        assertDoesNotThrow(() -> repository.clearHistory("nonexistent"));
    }

    // --- getAllSessions ---

    @Test
    void getAllSessionsReturnsSessionsInReverseChronologicalOrder() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Old", 1000L), "user-1", "Skill A");
        repository.saveMessage(new ChatMessage("m2", "s2", "skill-2", "user", "New", 3000L), "user-1", "Skill B");
        repository.saveMessage(new ChatMessage("m3", "s3", "skill-1", "user", "Mid", 2000L), "user-1", "Skill A");

        List<ChatSession> sessions = repository.getAllSessions("user-1");

        assertEquals(3, sessions.size());
        assertEquals("s2", sessions.get(0).id()); // timestamp 3000
        assertEquals("s3", sessions.get(1).id()); // timestamp 2000
        assertEquals("s1", sessions.get(2).id()); // timestamp 1000
    }

    @Test
    void getAllSessionsReturnsEmptyForUnknownUser() {
        assertTrue(repository.getAllSessions("unknown-user").isEmpty());
    }

    @Test
    void getAllSessionsIsolatedByUser() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "User1", 1000L), "user-1", "Skill");
        repository.saveMessage(new ChatMessage("m2", "s2", "skill-1", "user", "User2", 2000L), "user-2", "Skill");

        assertEquals(1, repository.getAllSessions("user-1").size());
        assertEquals(1, repository.getAllSessions("user-2").size());
    }

    // --- searchSessions ---

    @Test
    void searchSessionsFindsSessionsByMessageContent() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Translate this text", 1000L), "user-1", "Translation");
        repository.saveMessage(new ChatMessage("m2", "s2", "skill-2", "user", "Write some code", 2000L), "user-1", "Code");

        List<ChatSession> results = repository.searchSessions("Translate", "user-1");

        assertEquals(1, results.size());
        assertEquals("s1", results.get(0).id());
    }

    @Test
    void searchSessionsIsCaseInsensitive() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Hello World", 1000L), "user-1", "Skill");

        List<ChatSession> results = repository.searchSessions("hello", "user-1");

        assertEquals(1, results.size());
    }

    @Test
    void searchSessionsReturnsEmptyWhenNoMatch() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Hello", 1000L), "user-1", "Skill");

        List<ChatSession> results = repository.searchSessions("nonexistent", "user-1");

        assertTrue(results.isEmpty());
    }

    @Test
    void searchSessionsReturnsDistinctSessions() {
        // Two messages in the same session both match the keyword
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Hello world", 1000L), "user-1", "Skill");
        repository.saveMessage(new ChatMessage("m2", "s1", "skill-1", "assistant", "Hello back", 2000L), "user-1", "Skill");

        List<ChatSession> results = repository.searchSessions("Hello", "user-1");

        assertEquals(1, results.size());
    }

    @Test
    void searchSessionsOrderedByLastTimestampDesc() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "keyword old", 1000L), "user-1", "Skill A");
        repository.saveMessage(new ChatMessage("m2", "s2", "skill-2", "user", "keyword new", 3000L), "user-1", "Skill B");

        List<ChatSession> results = repository.searchSessions("keyword", "user-1");

        assertEquals(2, results.size());
        assertEquals("s2", results.get(0).id()); // newer first
        assertEquals("s1", results.get(1).id());
    }

    @Test
    void searchSessionsIsolatedByUser() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "shared keyword", 1000L), "user-1", "Skill");
        repository.saveMessage(new ChatMessage("m2", "s2", "skill-1", "user", "shared keyword", 2000L), "user-2", "Skill");

        List<ChatSession> results = repository.searchSessions("shared", "user-1");

        assertEquals(1, results.size());
        assertEquals("s1", results.get(0).id());
    }

    // --- deleteSession ---

    @Test
    void deleteSessionRemovesSessionAndMessages() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Hello", 1000L), "user-1", "Skill");
        repository.saveMessage(new ChatMessage("m2", "s1", "skill-1", "assistant", "Hi", 2000L), "user-1", "Skill");

        repository.deleteSession("s1");

        assertTrue(repository.getHistory("s1").isEmpty());
        assertTrue(repository.getAllSessions("user-1").isEmpty());
    }

    @Test
    void deleteSessionDoesNotAffectOtherSessions() {
        repository.saveMessage(new ChatMessage("m1", "s1", "skill-1", "user", "Session 1", 1000L), "user-1", "Skill");
        repository.saveMessage(new ChatMessage("m2", "s2", "skill-1", "user", "Session 2", 2000L), "user-1", "Skill");

        repository.deleteSession("s1");

        assertTrue(repository.getHistory("s1").isEmpty());
        assertEquals(1, repository.getHistory("s2").size());
        assertEquals(1, repository.getAllSessions("user-1").size());
        assertEquals("s2", repository.getAllSessions("user-1").get(0).id());
    }

    @Test
    void deleteNonexistentSessionIsNoOp() {
        assertDoesNotThrow(() -> repository.deleteSession("nonexistent"));
    }

    // --- saveMessage without userId/skillName (interface method) ---

    @Test
    void saveMessageWithoutExtraParamsCreatesSessionWithDefaults() {
        ChatMessage msg = new ChatMessage("m1", "s1", "skill-1", "user", "Hello", 1000L);
        repository.saveMessage(msg);

        // Session is created with empty userId, so getAllSessions("") should find it
        List<ChatSession> sessions = repository.getAllSessions("");
        assertEquals(1, sessions.size());
        assertEquals("skill-1", sessions.get(0).skillName()); // skillId used as skillName fallback
    }
}
