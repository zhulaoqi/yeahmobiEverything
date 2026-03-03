package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.chat.ChatMessage;
import net.jqwik.api.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ChatRepository conversation history round-trip.
 *
 * <p>Feature: yeahmobi-everything, Property 9: 对话历史 round-trip</p>
 *
 * <p><b>Validates: Requirements 3.6, 7.1, 7.2</b></p>
 *
 * <p>Uses a real SQLite in-memory database (no mocks) to verify that for any
 * sequence of ChatMessage objects with the same sessionId, saving them via
 * ChatRepository.saveMessage and then retrieving via ChatRepository.getHistory
 * returns all saved messages with matching fields.</p>
 */
class ChatRepositoryPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 9: 对话历史 round-trip
    void chatHistoryRoundTrip(
            @ForAll("chatMessageSequences") List<ChatMessage> messages
    ) throws SQLException, IOException {
        // **Validates: Requirements 3.6, 7.1, 7.2**

        // Arrange: fresh in-memory SQLite database for each trial
        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            ChatRepositoryImpl repository = new ChatRepositoryImpl(databaseManager);

            String userId = "test-user";
            String skillName = "test-skill";

            // Act: save all messages
            for (ChatMessage msg : messages) {
                repository.saveMessage(msg, userId, skillName);
            }

            // All messages share the same sessionId, retrieve history for that session
            if (messages.isEmpty()) {
                return; // nothing to verify for empty sequences
            }

            String sessionId = messages.get(0).sessionId();
            List<ChatMessage> retrieved = repository.getHistory(sessionId);

            // Assert 1: retrieved list should contain exactly the same number of messages
            assertEquals(messages.size(), retrieved.size(),
                    "Number of retrieved messages should match number of saved messages");

            // Assert 2: build a map of saved messages by ID for field-by-field comparison
            Map<String, ChatMessage> savedById = messages.stream()
                    .collect(Collectors.toMap(ChatMessage::id, m -> m));

            for (ChatMessage retrievedMsg : retrieved) {
                ChatMessage original = savedById.get(retrievedMsg.id());
                assertNotNull(original,
                        "Retrieved message with id '" + retrievedMsg.id() + "' should exist in saved messages");

                // Assert 3: all fields should match
                assertEquals(original.id(), retrievedMsg.id(), "id mismatch");
                assertEquals(original.sessionId(), retrievedMsg.sessionId(), "sessionId mismatch");
                assertEquals(original.skillId(), retrievedMsg.skillId(), "skillId mismatch");
                assertEquals(original.role(), retrievedMsg.role(), "role mismatch");
                assertEquals(original.content(), retrievedMsg.content(), "content mismatch");
                assertEquals(original.timestamp(), retrievedMsg.timestamp(), "timestamp mismatch");
            }

            // Assert 4: every saved message ID appears in the retrieved list
            Set<String> retrievedIds = retrieved.stream()
                    .map(ChatMessage::id)
                    .collect(Collectors.toSet());
            for (ChatMessage msg : messages) {
                assertTrue(retrievedIds.contains(msg.id()),
                        "Saved message with id '" + msg.id() + "' should appear in retrieved history");
            }

        } finally {
            databaseManager.close();
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<List<ChatMessage>> chatMessageSequences() {
        // Generate a fixed sessionId and skillId for the sequence
        Arbitrary<String> sessionIds = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(10).alpha().numeric()
                .map(s -> "session-" + s);

        Arbitrary<String> skillIds = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(10).alpha().numeric()
                .map(s -> "skill-" + s);

        return Combinators.combine(sessionIds, skillIds).as((sessionId, skillId) ->
                chatMessageList(sessionId, skillId)
        ).flatMap(arb -> arb);
    }

    private Arbitrary<List<ChatMessage>> chatMessageList(String sessionId, String skillId) {
        Arbitrary<String> roles = Arbitraries.of("user", "assistant");

        Arbitrary<String> contents = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(100)
                .alpha().numeric();

        Arbitrary<Long> timestamps = Arbitraries.longs()
                .between(1L, 1_000_000_000L);

        // Generate 1-20 messages, each with a unique ID
        return Combinators.combine(roles, contents, timestamps)
                .as((role, content, timestamp) -> new MessageSeed(role, content, timestamp))
                .list().ofMinSize(1).ofMaxSize(20)
                .map(seeds -> {
                    List<ChatMessage> messages = new ArrayList<>();
                    for (int i = 0; i < seeds.size(); i++) {
                        MessageSeed seed = seeds.get(i);
                        String uniqueId = "msg-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
                        messages.add(new ChatMessage(
                                uniqueId,
                                sessionId,
                                skillId,
                                seed.role(),
                                seed.content(),
                                seed.timestamp()
                        ));
                    }
                    return messages;
                });
    }

    private record MessageSeed(String role, String content, long timestamp) {}
}
