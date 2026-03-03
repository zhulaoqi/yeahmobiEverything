package com.yeahmobi.everything.chat;

/**
 * Represents a single chat message in a conversation.
 * <p>
 * Messages are stored in the local SQLite {@code chat_message} table.
 * Each message belongs to a session and is associated with a Skill.
 * The role indicates whether the message is from the user or the AI assistant.
 * </p>
 *
 * @param id        unique message identifier
 * @param sessionId the conversation session this message belongs to
 * @param skillId   the Skill associated with this message
 * @param role      the message sender role: "user" or "assistant"
 * @param content   the message text content
 * @param timestamp the message creation time in epoch milliseconds
 */
public record ChatMessage(
        String id,
        String sessionId,
        String skillId,
        String role,
        String content,
        long timestamp
) {}
