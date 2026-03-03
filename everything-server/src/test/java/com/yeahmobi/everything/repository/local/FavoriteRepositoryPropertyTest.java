package com.yeahmobi.everything.repository.local;

import net.jqwik.api.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for FavoriteRepository round-trip.
 *
 * <p>Feature: yeahmobi-everything, Property 6: 收藏 round-trip</p>
 *
 * <p><b>Validates: Requirements 2.5</b></p>
 *
 * <p>Uses a real SQLite in-memory database (no mocks) to verify that for any
 * user and Skill, calling addFavorite followed by isFavorite returns true,
 * and getFavoriteSkillIds contains that Skill ID.</p>
 */
class FavoriteRepositoryPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 6: 收藏 round-trip
    void favoriteRoundTrip(
            @ForAll("userIds") String userId,
            @ForAll("skillIds") String skillId
    ) throws SQLException, IOException {
        // **Validates: Requirements 2.5**

        // Arrange: fresh in-memory SQLite database for each trial
        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            FavoriteRepositoryImpl repository = new FavoriteRepositoryImpl(databaseManager);

            // Act: add the favorite
            repository.addFavorite(userId, skillId);

            // Assert: isFavorite should return true
            assertTrue(repository.isFavorite(userId, skillId),
                    "isFavorite should return true after addFavorite for userId='" + userId
                            + "', skillId='" + skillId + "'");

            // Assert: getFavoriteSkillIds should contain the skill ID
            List<String> favoriteIds = repository.getFavoriteSkillIds(userId);
            assertTrue(favoriteIds.contains(skillId),
                    "getFavoriteSkillIds should contain skillId='" + skillId
                            + "' after addFavorite for userId='" + userId + "'");
        } finally {
            databaseManager.close();
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(36)
                .alpha()
                .numeric();
    }

    @Provide
    Arbitrary<String> skillIds() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(36)
                .alpha()
                .numeric();
    }
}
