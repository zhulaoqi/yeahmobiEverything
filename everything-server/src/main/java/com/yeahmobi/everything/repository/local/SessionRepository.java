package com.yeahmobi.everything.repository.local;

import com.yeahmobi.everything.auth.Session;

import java.util.Optional;

/**
 * Repository interface for managing user sessions in the local SQLite database.
 * <p>
 * Sessions are cached locally so the user can be automatically logged in
 * on subsequent application launches without requiring network access.
 * </p>
 */
public interface SessionRepository {

    /**
     * Saves a session to the local database.
     * Any existing sessions are cleared before the new session is inserted.
     *
     * @param session the session to save
     */
    void saveSession(Session session);

    /**
     * Loads the most recent session from the local database.
     *
     * @return an Optional containing the session if one exists, or empty otherwise
     */
    Optional<Session> loadSession();

    /**
     * Clears all sessions from the local database.
     */
    void clearSession();
}
