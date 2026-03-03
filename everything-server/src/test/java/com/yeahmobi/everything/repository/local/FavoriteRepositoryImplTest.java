package com.yeahmobi.everything.repository.local;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FavoriteRepositoryImpl} using an in-memory SQLite database.
 */
class FavoriteRepositoryImplTest {

    private LocalDatabaseManager databaseManager;
    private FavoriteRepositoryImpl repository;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        databaseManager = new LocalDatabaseManager(":memory:");
        databaseManager.initialize();
        repository = new FavoriteRepositoryImpl(databaseManager);
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Test
    void addFavoriteAndCheckIsFavorite() {
        repository.addFavorite("user-1", "skill-1");

        assertTrue(repository.isFavorite("user-1", "skill-1"));
    }

    @Test
    void isFavoriteReturnsFalseWhenNotFavorited() {
        assertFalse(repository.isFavorite("user-1", "skill-1"));
    }

    @Test
    void addFavoriteTwiceIsIdempotent() {
        repository.addFavorite("user-1", "skill-1");
        repository.addFavorite("user-1", "skill-1");

        assertTrue(repository.isFavorite("user-1", "skill-1"));
        List<String> ids = repository.getFavoriteSkillIds("user-1");
        assertEquals(1, ids.size());
    }

    @Test
    void removeFavorite() {
        repository.addFavorite("user-1", "skill-1");
        assertTrue(repository.isFavorite("user-1", "skill-1"));

        repository.removeFavorite("user-1", "skill-1");
        assertFalse(repository.isFavorite("user-1", "skill-1"));
    }

    @Test
    void removeFavoriteWhenNotFavoritedIsNoOp() {
        assertDoesNotThrow(() -> repository.removeFavorite("user-1", "skill-1"));
    }

    @Test
    void getFavoriteSkillIdsReturnsAllFavorites() {
        repository.addFavorite("user-1", "skill-1");
        repository.addFavorite("user-1", "skill-2");
        repository.addFavorite("user-1", "skill-3");

        List<String> ids = repository.getFavoriteSkillIds("user-1");

        assertEquals(3, ids.size());
        assertTrue(ids.contains("skill-1"));
        assertTrue(ids.contains("skill-2"));
        assertTrue(ids.contains("skill-3"));
    }

    @Test
    void getFavoriteSkillIdsReturnsEmptyWhenNoFavorites() {
        List<String> ids = repository.getFavoriteSkillIds("user-1");
        assertTrue(ids.isEmpty());
    }

    @Test
    void favoritesAreIsolatedPerUser() {
        repository.addFavorite("user-1", "skill-1");
        repository.addFavorite("user-2", "skill-2");

        assertTrue(repository.isFavorite("user-1", "skill-1"));
        assertFalse(repository.isFavorite("user-1", "skill-2"));
        assertFalse(repository.isFavorite("user-2", "skill-1"));
        assertTrue(repository.isFavorite("user-2", "skill-2"));

        assertEquals(1, repository.getFavoriteSkillIds("user-1").size());
        assertEquals(1, repository.getFavoriteSkillIds("user-2").size());
    }

    @Test
    void removeFavoriteDoesNotAffectOtherUsers() {
        repository.addFavorite("user-1", "skill-1");
        repository.addFavorite("user-2", "skill-1");

        repository.removeFavorite("user-1", "skill-1");

        assertFalse(repository.isFavorite("user-1", "skill-1"));
        assertTrue(repository.isFavorite("user-2", "skill-1"));
    }

    @Test
    void addAndRemoveMultipleFavorites() {
        repository.addFavorite("user-1", "skill-1");
        repository.addFavorite("user-1", "skill-2");
        repository.addFavorite("user-1", "skill-3");

        repository.removeFavorite("user-1", "skill-2");

        List<String> ids = repository.getFavoriteSkillIds("user-1");
        assertEquals(2, ids.size());
        assertTrue(ids.contains("skill-1"));
        assertFalse(ids.contains("skill-2"));
        assertTrue(ids.contains("skill-3"));
    }
}
