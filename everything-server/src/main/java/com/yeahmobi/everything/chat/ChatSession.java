package com.yeahmobi.everything.chat;

/**
 * Represents a chat conversation session.
 * <p>
 * Sessions are stored in the local SQLite {@code chat_session} table.
 * Each session is associated with a Skill and tracks the last message
 * for display in the history list.
 * </p>
 *
 * @param id            unique session identifier
 * @param skillId       the Skill this session is associated with
 * @param skillName     the display name of the associated Skill
 * @param lastMessage   the content of the most recent message in this session
 * @param lastTimestamp the timestamp of the most recent message in epoch milliseconds
 */
public record ChatSession(
        String id,
        String skillId,
        String skillName,
        String lastMessage,
        long lastTimestamp
) {}
