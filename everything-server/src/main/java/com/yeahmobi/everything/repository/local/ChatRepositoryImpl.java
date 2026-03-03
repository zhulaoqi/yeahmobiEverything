package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.chat.ChatMessage;
import com.yeahmobi.everything.chat.ChatSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of {@link ChatRepository}.
 * <p>
 * Uses {@link LocalDatabaseManager} to obtain a connection to the local
 * SQLite database. Messages are stored in the {@code chat_message} table
 * and sessions in the {@code chat_session} table.
 * </p>
 * <p>
 * When saving a message, the associated session is automatically created
 * (if it does not exist) or updated with the latest message content and
 * timestamp. Session creation via {@link #saveMessage(ChatMessage)} uses
 * the skill ID as a placeholder for the skill name and an empty string
 * for the user ID. For full session metadata, use
 * {@link #saveMessage(ChatMessage, String, String)} instead.
 * </p>
 */
public class ChatRepositoryImpl implements ChatRepository {

    private static final String INSERT_MESSAGE_SQL =
            "INSERT INTO chat_message (id, session_id, skill_id, role, content, timestamp) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPSERT_SESSION_SQL =
            "INSERT INTO chat_session (id, user_id, skill_id, skill_name, last_message, last_timestamp) "
                    + "VALUES (?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT(id) DO UPDATE SET "
                    + "last_message = excluded.last_message, "
                    + "last_timestamp = excluded.last_timestamp";

    private static final String SELECT_HISTORY_SQL =
            "SELECT id, session_id, skill_id, role, content, timestamp "
                    + "FROM chat_message WHERE session_id = ? ORDER BY timestamp ASC";

    private static final String DELETE_MESSAGES_SQL =
            "DELETE FROM chat_message WHERE session_id = ?";

    private static final String SELECT_ALL_SESSIONS_SQL =
            "SELECT id, skill_id, skill_name, last_message, last_timestamp "
                    + "FROM chat_session WHERE user_id = ? ORDER BY last_timestamp DESC";

    private static final String SEARCH_SESSIONS_SQL =
            "SELECT DISTINCT cs.id, cs.skill_id, cs.skill_name, cs.last_message, cs.last_timestamp "
                    + "FROM chat_session cs "
                    + "INNER JOIN chat_message cm ON cs.id = cm.session_id "
                    + "WHERE cs.user_id = ? AND cm.content LIKE ? "
                    + "ORDER BY cs.last_timestamp DESC";

    private static final String DELETE_SESSION_SQL =
            "DELETE FROM chat_session WHERE id = ?";

    private static final String DELETE_SESSION_MESSAGES_SQL =
            "DELETE FROM chat_message WHERE session_id = ?";

    private static final String SELECT_USED_SKILL_IDS_SQL =
            "SELECT DISTINCT cm.skill_id "
                    + "FROM chat_message cm "
                    + "INNER JOIN chat_session cs ON cm.session_id = cs.id "
                    + "WHERE cs.user_id = ?";

    private final LocalDatabaseManager databaseManager;

    /**
     * Creates a ChatRepositoryImpl backed by the given database manager.
     *
     * @param databaseManager the local SQLite database manager
     */
    public ChatRepositoryImpl(LocalDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void saveMessage(ChatMessage message) {
        saveMessage(message, "", message.skillId());
    }

    /**
     * Saves a chat message and upserts the associated session with full metadata.
     * <p>
     * If the session does not exist, it is created with the provided user ID
     * and skill name. If it already exists, only the last message and timestamp
     * are updated.
     * </p>
     *
     * @param message   the chat message to save
     * @param userId    the user ID for session creation
     * @param skillName the skill display name for session creation
     */
    public void saveMessage(ChatMessage message, String userId, String skillName) {
        try {
            Connection conn = databaseManager.getConnection();

            // Upsert the session
            try (PreparedStatement sessionStmt = conn.prepareStatement(UPSERT_SESSION_SQL)) {
                sessionStmt.setString(1, message.sessionId());
                sessionStmt.setString(2, userId);
                sessionStmt.setString(3, message.skillId());
                sessionStmt.setString(4, skillName);
                sessionStmt.setString(5, message.content());
                sessionStmt.setLong(6, message.timestamp());
                sessionStmt.executeUpdate();
            }

            // Insert the message
            try (PreparedStatement msgStmt = conn.prepareStatement(INSERT_MESSAGE_SQL)) {
                msgStmt.setString(1, message.id());
                msgStmt.setString(2, message.sessionId());
                msgStmt.setString(3, message.skillId());
                msgStmt.setString(4, message.role());
                msgStmt.setString(5, message.content());
                msgStmt.setLong(6, message.timestamp());
                msgStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save chat message", e);
        }
    }

    @Override
    public List<ChatMessage> getHistory(String sessionId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_HISTORY_SQL)) {
                stmt.setString(1, sessionId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<ChatMessage> messages = new ArrayList<>();
                    while (rs.next()) {
                        messages.add(new ChatMessage(
                                rs.getString("id"),
                                rs.getString("session_id"),
                                rs.getString("skill_id"),
                                rs.getString("role"),
                                rs.getString("content"),
                                rs.getLong("timestamp")
                        ));
                    }
                    return messages;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get chat history", e);
        }
    }

    @Override
    public void clearHistory(String sessionId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_MESSAGES_SQL)) {
                stmt.setString(1, sessionId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear chat history", e);
        }
    }

    @Override
    public List<ChatSession> getAllSessions(String userId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SESSIONS_SQL)) {
                stmt.setString(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<ChatSession> sessions = new ArrayList<>();
                    while (rs.next()) {
                        sessions.add(new ChatSession(
                                rs.getString("id"),
                                rs.getString("skill_id"),
                                rs.getString("skill_name"),
                                rs.getString("last_message"),
                                rs.getLong("last_timestamp")
                        ));
                    }
                    return sessions;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get all sessions", e);
        }
    }

    @Override
    public List<ChatSession> searchSessions(String keyword, String userId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SEARCH_SESSIONS_SQL)) {
                stmt.setString(1, userId);
                stmt.setString(2, "%" + keyword + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    List<ChatSession> sessions = new ArrayList<>();
                    while (rs.next()) {
                        sessions.add(new ChatSession(
                                rs.getString("id"),
                                rs.getString("skill_id"),
                                rs.getString("skill_name"),
                                rs.getString("last_message"),
                                rs.getLong("last_timestamp")
                        ));
                    }
                    return sessions;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search sessions", e);
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        try {
            Connection conn = databaseManager.getConnection();
            // Delete messages first (in case foreign key cascade is not enabled)
            try (PreparedStatement msgStmt = conn.prepareStatement(DELETE_SESSION_MESSAGES_SQL)) {
                msgStmt.setString(1, sessionId);
                msgStmt.executeUpdate();
            }
            // Delete the session
            try (PreparedStatement sessionStmt = conn.prepareStatement(DELETE_SESSION_SQL)) {
                sessionStmt.setString(1, sessionId);
                sessionStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete session", e);
        }
    }

    @Override
    public List<String> getUsedSkillIds(String userId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_USED_SKILL_IDS_SQL)) {
                stmt.setString(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<String> skillIds = new ArrayList<>();
                    while (rs.next()) {
                        skillIds.add(rs.getString("skill_id"));
                    }
                    return skillIds;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get used skill IDs", e);
        }
    }
}
