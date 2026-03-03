package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.chat.ChatMessage;
import com.yeahmobi.everything.chat.ChatSession;

import java.util.List;

/**
 * Repository interface for managing chat messages and sessions in the local SQLite database.
 * <p>
 * Chat data is stored locally so users can review conversation history
 * and continue previous conversations without network access.
 * </p>
 */
public interface ChatRepository {

    /**
     * Saves a chat message to the local database.
     * Also creates or updates the associated chat session with the latest
     * message content and timestamp.
     *
     * @param message the chat message to save
     */
    void saveMessage(ChatMessage message);

    /**
     * Saves a chat message with full session metadata.
     *
     * @param message   the chat message to save
     * @param userId    the owner user ID
     * @param skillName display skill name
     */
    void saveMessage(ChatMessage message, String userId, String skillName);

    /**
     * Retrieves all messages for a given session, ordered by timestamp ascending.
     *
     * @param sessionId the session ID to retrieve messages for
     * @return a list of messages in chronological order
     */
    List<ChatMessage> getHistory(String sessionId);

    /**
     * Deletes all messages for a given session.
     * The session record itself is not removed.
     *
     * @param sessionId the session ID whose messages should be cleared
     */
    void clearHistory(String sessionId);

    /**
     * Retrieves all chat sessions for a given user, ordered by last timestamp descending.
     *
     * @param userId the user ID
     * @return a list of sessions, most recent first
     */
    List<ChatSession> getAllSessions(String userId);

    /**
     * Searches for sessions where any message content contains the given keyword
     * (case-insensitive), ordered by last timestamp descending.
     *
     * @param keyword the search keyword
     * @param userId  the user ID
     * @return a list of matching sessions, most recent first
     */
    List<ChatSession> searchSessions(String keyword, String userId);

    /**
     * Deletes a session and all its associated messages.
     *
     * @param sessionId the session ID to delete
     */
    void deleteSession(String sessionId);

    /**
     * Gets all distinct skill IDs that have chat messages for the given user.
     *
     * @param userId the user ID
     * @return a list of skill IDs that have been used (have chat history)
     */
    List<String> getUsedSkillIds(String userId);
}
