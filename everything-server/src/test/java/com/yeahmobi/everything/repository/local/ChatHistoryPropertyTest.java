package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.chat.ChatMessage;
import com.yeahmobi.everything.chat.ChatSession;
import net.jqwik.api.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ChatRepository history operations:
 * sorting, searching, and deletion.
 *
 * <p>Feature: yeahmobi-everything</p>
 * <ul>
 *   <li>Property 16: 历史记录时间倒序</li>
 *   <li>Property 17: 历史记录搜索正确性</li>
 *   <li>Property 18: 历史记录删除</li>
 * </ul>
 *
 * <p>Uses a real SQLite in-memory database (no mocks).</p>
 */
class ChatHistoryPropertyTest {

    // ========================================================================
    // Property 16: 历史记录时间倒序
    // ========================================================================

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 16: 历史记录时间倒序
    void sessionsReturnedInDescendingTimestampOrder(
            @ForAll("sessionDataSets") List<SessionData> sessionDataList
    ) throws SQLException, IOException {
        // **Validates: Requirements 8.2**

        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            ChatRepositoryImpl repository = new ChatRepositoryImpl(databaseManager);

            String userId = "test-user";

            // Save messages for each session; the last message determines the session's lastTimestamp
            for (SessionData sd : sessionDataList) {
                for (ChatMessage msg : sd.messages()) {
                    repository.saveMessage(msg, userId, sd.skillName());
                }
            }

            // Act
            List<ChatSession> sessions = repository.getAllSessions(userId);

            // Assert: sessions should be ordered by lastTimestamp descending
            for (int i = 0; i < sessions.size() - 1; i++) {
                assertTrue(sessions.get(i).lastTimestamp() >= sessions.get(i + 1).lastTimestamp(),
                        "Session at index " + i + " (timestamp=" + sessions.get(i).lastTimestamp()
                                + ") should have timestamp >= session at index " + (i + 1)
                                + " (timestamp=" + sessions.get(i + 1).lastTimestamp() + ")");
            }

            // Also verify the count matches the number of distinct sessions we created
            assertEquals(sessionDataList.size(), sessions.size(),
                    "Number of sessions returned should match number of distinct sessions created");

        } finally {
            databaseManager.close();
        }
    }

    // ========================================================================
    // Property 17: 历史记录搜索正确性
    // ========================================================================

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 17: 历史记录搜索正确性
    void searchSessionsReturnsOnlySessionsWithMatchingMessages(
            @ForAll("searchScenarios") SearchScenario scenario
    ) throws SQLException, IOException {
        // **Validates: Requirements 8.4**

        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            ChatRepositoryImpl repository = new ChatRepositoryImpl(databaseManager);

            String userId = "test-user";

            // Save all messages across all sessions
            for (SessionData sd : scenario.sessionDataList()) {
                for (ChatMessage msg : sd.messages()) {
                    repository.saveMessage(msg, userId, sd.skillName());
                }
            }

            // Act: search for the keyword
            List<ChatSession> results = repository.searchSessions(scenario.keyword(), userId);

            // Assert: every returned session must have at least one message containing the keyword
            Set<String> resultSessionIds = results.stream()
                    .map(ChatSession::id)
                    .collect(Collectors.toSet());

            for (String sessionId : resultSessionIds) {
                List<ChatMessage> history = repository.getHistory(sessionId);
                boolean hasMatch = history.stream()
                        .anyMatch(msg -> msg.content().toLowerCase()
                                .contains(scenario.keyword().toLowerCase()));
                assertTrue(hasMatch,
                        "Session '" + sessionId + "' returned by searchSessions should have "
                                + "at least one message containing keyword '" + scenario.keyword() + "'");
            }

        } finally {
            databaseManager.close();
        }
    }

    // ========================================================================
    // Property 18: 历史记录删除
    // ========================================================================

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 18: 历史记录删除
    void deleteSessionRemovesSessionAndAllItsMessages(
            @ForAll("deleteScenarios") DeleteScenario scenario
    ) throws SQLException, IOException {
        // **Validates: Requirements 8.5**

        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            ChatRepositoryImpl repository = new ChatRepositoryImpl(databaseManager);

            String userId = "test-user";

            // Save all sessions and their messages
            for (SessionData sd : scenario.allSessions()) {
                for (ChatMessage msg : sd.messages()) {
                    repository.saveMessage(msg, userId, sd.skillName());
                }
            }

            String sessionToDelete = scenario.sessionIdToDelete();

            // Act: delete the target session
            repository.deleteSession(sessionToDelete);

            // Assert 1: getAllSessions should not contain the deleted session
            List<ChatSession> remainingSessions = repository.getAllSessions(userId);
            Set<String> remainingIds = remainingSessions.stream()
                    .map(ChatSession::id)
                    .collect(Collectors.toSet());
            assertFalse(remainingIds.contains(sessionToDelete),
                    "Deleted session '" + sessionToDelete + "' should not appear in getAllSessions");

            // Assert 2: all messages for the deleted session should be gone
            List<ChatMessage> deletedMessages = repository.getHistory(sessionToDelete);
            assertTrue(deletedMessages.isEmpty(),
                    "All messages for deleted session '" + sessionToDelete + "' should be removed");

            // Assert 3: other sessions should still exist with their messages intact
            for (SessionData sd : scenario.allSessions()) {
                if (!sd.sessionId().equals(sessionToDelete)) {
                    assertTrue(remainingIds.contains(sd.sessionId()),
                            "Non-deleted session '" + sd.sessionId() + "' should still exist");
                    List<ChatMessage> history = repository.getHistory(sd.sessionId());
                    assertEquals(sd.messages().size(), history.size(),
                            "Non-deleted session '" + sd.sessionId()
                                    + "' should retain all its messages");
                }
            }

        } finally {
            databaseManager.close();
        }
    }

    // ========================================================================
    // Data records
    // ========================================================================

    private record SessionData(String sessionId, String skillId, String skillName,
                               List<ChatMessage> messages) {}

    private record SearchScenario(List<SessionData> sessionDataList, String keyword) {}

    private record DeleteScenario(List<SessionData> allSessions, String sessionIdToDelete) {}

    // ========================================================================
    // Arbitrary Providers
    // ========================================================================

    @Provide
    Arbitrary<List<SessionData>> sessionDataSets() {
        // Generate 1-8 sessions, each with 1-5 messages and distinct timestamps
        return Arbitraries.integers().between(1, 8).flatMap(sessionCount -> {
            List<Arbitrary<SessionData>> sessionArbitraries = new ArrayList<>();
            for (int i = 0; i < sessionCount; i++) {
                final int idx = i;
                sessionArbitraries.add(sessionData("session-" + idx, "skill-" + idx));
            }
            return Combinators.combine(sessionArbitraries).as(list -> list);
        });
    }

    @Provide
    Arbitrary<SearchScenario> searchScenarios() {
        // Generate a keyword and a set of sessions where some messages contain the keyword
        Arbitrary<String> keywords = Arbitraries.strings()
                .ofMinLength(2).ofMaxLength(8)
                .alpha();

        return keywords.flatMap(keyword -> {
            // Generate 1-5 sessions
            return Arbitraries.integers().between(1, 5).flatMap(sessionCount -> {
                List<Arbitrary<SessionData>> sessionArbitraries = new ArrayList<>();
                for (int i = 0; i < sessionCount; i++) {
                    final int idx = i;
                    // Some sessions will have messages containing the keyword, some won't
                    sessionArbitraries.add(
                            Arbitraries.oneOf(
                                    sessionDataWithKeyword("session-" + idx, "skill-" + idx, keyword),
                                    sessionData("session-" + idx, "skill-" + idx)
                            )
                    );
                }
                return Combinators.combine(sessionArbitraries)
                        .as(list -> new SearchScenario(list, keyword));
            });
        });
    }

    @Provide
    Arbitrary<DeleteScenario> deleteScenarios() {
        // Generate 1-6 sessions and pick one to delete
        return Arbitraries.integers().between(1, 6).flatMap(sessionCount -> {
            List<Arbitrary<SessionData>> sessionArbitraries = new ArrayList<>();
            for (int i = 0; i < sessionCount; i++) {
                sessionArbitraries.add(sessionData("session-" + i, "skill-" + i));
            }
            return Combinators.combine(sessionArbitraries).as(list -> list)
                    .flatMap(sessions -> {
                        // Pick a random session to delete
                        return Arbitraries.integers().between(0, sessions.size() - 1)
                                .map(deleteIdx -> new DeleteScenario(
                                        sessions, sessions.get(deleteIdx).sessionId()));
                    });
        });
    }

    // ---- Helper Arbitrary builders ----

    private Arbitrary<SessionData> sessionData(String sessionId, String skillId) {
        Arbitrary<String> roles = Arbitraries.of("user", "assistant");
        Arbitrary<String> contents = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(50).alpha().numeric();
        Arbitrary<Long> timestamps = Arbitraries.longs().between(1L, 1_000_000_000L);
        Arbitrary<String> skillNames = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(15).alpha();

        return skillNames.flatMap(skillName ->
                Combinators.combine(roles, contents, timestamps)
                        .as((role, content, ts) -> new Object[]{role, content, ts})
                        .list().ofMinSize(1).ofMaxSize(5)
                        .map(seeds -> {
                            List<ChatMessage> messages = new ArrayList<>();
                            for (int i = 0; i < seeds.size(); i++) {
                                Object[] seed = seeds.get(i);
                                messages.add(new ChatMessage(
                                        sessionId + "-msg-" + i + "-" + UUID.randomUUID().toString().substring(0, 6),
                                        sessionId,
                                        skillId,
                                        (String) seed[0],
                                        (String) seed[1],
                                        (long) seed[2]
                                ));
                            }
                            return new SessionData(sessionId, skillId, skillName, messages);
                        })
        );
    }

    private Arbitrary<SessionData> sessionDataWithKeyword(String sessionId, String skillId, String keyword) {
        Arbitrary<String> roles = Arbitraries.of("user", "assistant");
        Arbitrary<Long> timestamps = Arbitraries.longs().between(1L, 1_000_000_000L);
        Arbitrary<String> skillNames = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(15).alpha();

        // Generate content that contains the keyword
        Arbitrary<String> contentsWithKeyword = Arbitraries.strings()
                .ofMinLength(0).ofMaxLength(20).alpha()
                .flatMap(prefix -> Arbitraries.strings()
                        .ofMinLength(0).ofMaxLength(20).alpha()
                        .map(suffix -> prefix + keyword + suffix));

        // Generate some regular content too
        Arbitrary<String> regularContents = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(50).alpha().numeric();

        return skillNames.flatMap(skillName ->
                Combinators.combine(roles, timestamps)
                        .as((role, ts) -> new Object[]{role, ts})
                        .list().ofMinSize(1).ofMaxSize(5)
                        .flatMap(seeds -> {
                            // Ensure at least one message contains the keyword
                            return Arbitraries.integers().between(0, seeds.size() - 1)
                                    .flatMap(keywordIdx -> {
                                        List<Arbitrary<ChatMessage>> msgArbitraries = new ArrayList<>();
                                        for (int i = 0; i < seeds.size(); i++) {
                                            Object[] seed = seeds.get(i);
                                            String role = (String) seed[0];
                                            long ts = (long) seed[1];
                                            String msgId = sessionId + "-msg-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);

                                            Arbitrary<String> contentArb = (i == keywordIdx)
                                                    ? contentsWithKeyword
                                                    : regularContents;

                                            msgArbitraries.add(contentArb.map(content ->
                                                    new ChatMessage(msgId, sessionId, skillId, role, content, ts)));
                                        }
                                        return Combinators.combine(msgArbitraries).as(list ->
                                                new SessionData(sessionId, skillId, skillName, list));
                                    });
                        })
        );
    }
}
