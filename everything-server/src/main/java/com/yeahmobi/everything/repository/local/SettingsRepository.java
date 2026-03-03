package com.yeahmobi.everything.repository.local;

import java.util.Optional;

/**
 * Repository interface for managing user settings in the local SQLite database.
 * <p>
 * Settings are stored as key-value pairs. Changes are persisted immediately
 * so they survive application restarts.
 * </p>
 */
public interface SettingsRepository {

    /**
     * Saves a setting. If a setting with the given key already exists,
     * its value is replaced.
     *
     * @param key   the setting key
     * @param value the setting value
     */
    void saveSetting(String key, String value);

    /**
     * Retrieves the value for the given setting key.
     *
     * @param key the setting key
     * @return an Optional containing the value if the key exists, or empty otherwise
     */
    Optional<String> getSetting(String key);
}
