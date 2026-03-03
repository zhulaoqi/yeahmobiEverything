package com.yeahmobi.everything.repository.local;

import net.jqwik.api.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for SettingsRepository round-trip behavior.
 *
 * <p><b>Feature: yeahmobi-everything, Property 15: 用户设置 round-trip</b></p>
 *
 * <p><b>Validates: Requirements 7.3</b></p>
 *
 * <p>Uses a real SQLite in-memory database to verify that saving a setting
 * and then reading it back returns the original value.</p>
 */
class SettingsRepositoryRoundTripPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 15: 用户设置 round-trip
    void savedSettingCanBeRetrieved(
            @ForAll("settingKeys") String key,
            @ForAll("settingValues") String value) throws SQLException, IOException {
        // **Validates: Requirements 7.3**

        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            SettingsRepositoryImpl repository = new SettingsRepositoryImpl(databaseManager);

            // Save the setting
            repository.saveSetting(key, value);

            // Retrieve and verify
            Optional<String> result = repository.getSetting(key);
            assertTrue(result.isPresent(), "Setting should be present after saving");
            assertEquals(value, result.get(), "Retrieved value should match saved value");
        } finally {
            databaseManager.close();
        }
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 15: 用户设置 round-trip
    void overwrittenSettingReturnsLatestValue(
            @ForAll("settingKeys") String key,
            @ForAll("settingValues") String firstValue,
            @ForAll("settingValues") String secondValue) throws SQLException, IOException {
        // **Validates: Requirements 7.3**

        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            SettingsRepositoryImpl repository = new SettingsRepositoryImpl(databaseManager);

            // Save first value, then overwrite with second
            repository.saveSetting(key, firstValue);
            repository.saveSetting(key, secondValue);

            // Should return the latest value
            Optional<String> result = repository.getSetting(key);
            assertTrue(result.isPresent(), "Setting should be present after overwrite");
            assertEquals(secondValue, result.get(), "Retrieved value should match the latest saved value");
        } finally {
            databaseManager.close();
        }
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 15: 用户设置 round-trip
    void nonExistentKeyReturnsEmpty(@ForAll("settingKeys") String key) throws SQLException, IOException {
        // **Validates: Requirements 7.3**

        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            SettingsRepositoryImpl repository = new SettingsRepositoryImpl(databaseManager);

            Optional<String> result = repository.getSetting(key);
            assertTrue(result.isEmpty(), "Non-existent key should return empty Optional");
        } finally {
            databaseManager.close();
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> settingKeys() {
        return Arbitraries.of("theme", "language", "fontSize", "autoSave",
                "notifications", "darkMode", "sidebarWidth", "defaultSkill");
    }

    @Provide
    Arbitrary<String> settingValues() {
        return Arbitraries.of("light", "dark", "en", "zh", "14", "16", "true", "false",
                "200", "300", "translator", "code-assistant");
    }
}
