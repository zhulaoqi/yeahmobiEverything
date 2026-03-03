package com.yeahmobi.everything.chat;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for chat operations.
 * <p>
 * Handles sending messages to the LLM API (with Skill context and optional
 * knowledge base content for KNOWLEDGE_RAG skills), managing chat history,
 * sessions, and building knowledge-augmented context.
 * </p>
 */
public interface ChatService {

    /**
     * Sends a user message to the LLM API, returning the response asynchronously.
     * <p>
     * The request includes the user message, Skill context (prompt template),
     * conversation history, and — for KNOWLEDGE_RAG skills — automatically
     * injected knowledge base content.
     * </p>
     *
     * @param skillId     the Skill ID to use for context
     * @param userMessage the user's input message
     * @param history     the conversation history for context continuity
     * @return a CompletableFuture containing the chat response
     */
    CompletableFuture<ChatResponse> sendMessage(String skillId, String userMessage, List<ChatMessage> history);

    /**
     * Sends a user message to the LLM API with streaming output.
     *
     * @param skillId     the Skill ID to use for context
     * @param userMessage the user's input message
     * @param history     the conversation history for context continuity
     * @param onDelta     callback invoked for each streamed content chunk
     * @return a CompletableFuture containing the final chat response
     */
    CompletableFuture<ChatResponse> sendMessageStream(
            String skillId,
            String userMessage,
            List<ChatMessage> history,
            java.util.function.Consumer<String> onDelta
    );

    /**
     * Retrieves the chat history for a given Skill session.
     *
     * @param sessionId the session ID
     * @return a list of chat messages in chronological order
     */
    List<ChatMessage> getChatHistory(String sessionId);

    /**
     * Saves a chat message to the local database.
     *
     * @param message the message to save
     */
    void saveMessage(ChatMessage message);

    /**
     * Saves a chat message with full session metadata.
     *
     * @param message   the message to save
     * @param userId    the owner user ID of this session
     * @param skillName display name of the skill
     */
    void saveMessage(ChatMessage message, String userId, String skillName);

    /**
     * Clears all messages for a given session.
     *
     * @param sessionId the session ID whose messages should be cleared
     */
    void clearHistory(String sessionId);

    /**
     * Gets the most recent session for a skill.
     *
     * @param skillId the skill ID
     * @param userId  the user ID
     * @return the most recent session ID, or null if none exists
     */
    String getRecentSessionForSkill(String skillId, String userId);

    /**
     * Gets all chat sessions for a user.
     *
     * @param userId the user ID
     * @return a list of sessions ordered by last timestamp descending
     */
    List<ChatSession> getAllSessions(String userId);

    /**
     * Searches chat sessions by keyword.
     *
     * @param keyword the search keyword
     * @param userId  the user ID
     * @return a list of matching sessions
     */
    List<ChatSession> searchSessions(String keyword, String userId);

    /**
     * Deletes a chat session and all its messages.
     *
     * @param sessionId the session ID to delete
     */
    void deleteSession(String sessionId);

    /**
     * Builds a context string that includes knowledge base content for the given Skill.
     * <p>
     * For KNOWLEDGE_RAG skills, retrieves the merged knowledge text from Redis cache
     * first; if not cached, fetches from the database and caches the result.
     * The returned string combines the user message with the knowledge context.
     * </p>
     *
     * @param skillId     the Skill ID
     * @param userMessage the user's input message
     * @return a context string containing the user message and any knowledge base content
     */
    String buildContextWithKnowledge(String skillId, String userMessage);
}
