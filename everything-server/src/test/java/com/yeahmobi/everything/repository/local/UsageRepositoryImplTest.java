package com.yeahmobi.everything.repository.local;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UsageRepositoryImpl} using an in-memory SQLite database.
 */
class UsageRepositoryImplTest {

    private LocalDatabaseManager databaseManager;
    private UsageRepositoryImpl repository;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        databaseManager = new LocalDatabaseManager(":memory:");
        databaseManager.initialize();
        repository = new UsageRepositoryImpl(databaseManager);
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Test
    void recordUsageAndGetRecent() {
        repository.recordUsage("user-1", "skill-1");

        List<String> recent = repository.getRecentSkillIds("user-1", 10);

        assertEquals(1, recent.size());
        assertEquals("skill-1", recent.get(0));
    }

    @Test
    void getRecentSkillIdsReturnsEmptyWhenNoUsage() {
        List<String> recent = repository.getRecentSkillIds("user-1", 10);
        assertTrue(recent.isEmpty());
    }

    @Test
    void getRecentSkillIdsReturnsDistinctSkills() throws InterruptedException {
        repository.recordUsage("user-1", "skill-1");
        Thread.sleep(10); // Ensure different timestamps
        repository.recordUsage("user-1", "skill-1");
        Thread.sleep(10);
        repository.recordUsage("user-1", "skill-2");

        List<String> recent = repository.getRecentSkillIds("user-1", 10);

        assertEquals(2, recent.size());
        // skill-2 was used most recently, so it should be first
        assertEquals("skill-2", recent.get(0));
        assertEquals("skill-1", recent.get(1));
    }

    @Test
    void getRecentSkillIdsRespectsLimit() throws InterruptedException {
        repository.recordUsage("user-1", "skill-1");
        Thread.sleep(10);
        repository.recordUsage("user-1", "skill-2");
        Thread.sleep(10);
        repository.recordUsage("user-1", "skill-3");

        List<String> recent = repository.getRecentSkillIds("user-1", 2);

        assertEquals(2, recent.size());
        assertEquals("skill-3", recent.get(0));
        assertEquals("skill-2", recent.get(1));
    }

    @Test
    void getRecentSkillIdsOrderedByMostRecentFirst() throws InterruptedException {
        repository.recordUsage("user-1", "skill-a");
        Thread.sleep(10);
        repository.recordUsage("user-1", "skill-b");
        Thread.sleep(10);
        repository.recordUsage("user-1", "skill-c");

        List<String> recent = repository.getRecentSkillIds("user-1", 10);

        assertEquals(3, recent.size());
        assertEquals("skill-c", recent.get(0));
        assertEquals("skill-b", recent.get(1));
        assertEquals("skill-a", recent.get(2));
    }

    @Test
    void usageIsIsolatedPerUser() {
        repository.recordUsage("user-1", "skill-1");
        repository.recordUsage("user-2", "skill-2");

        List<String> user1Recent = repository.getRecentSkillIds("user-1", 10);
        List<String> user2Recent = repository.getRecentSkillIds("user-2", 10);

        assertEquals(1, user1Recent.size());
        assertEquals("skill-1", user1Recent.get(0));
        assertEquals(1, user2Recent.size());
        assertEquals("skill-2", user2Recent.get(0));
    }

    @Test
    void recentUsageUpdatesOrderWhenSkillReused() throws InterruptedException {
        repository.recordUsage("user-1", "skill-1");
        Thread.sleep(10);
        repository.recordUsage("user-1", "skill-2");
        Thread.sleep(10);
        // Re-use skill-1, making it the most recent
        repository.recordUsage("user-1", "skill-1");

        List<String> recent = repository.getRecentSkillIds("user-1", 10);

        assertEquals(2, recent.size());
        assertEquals("skill-1", recent.get(0));
        assertEquals("skill-2", recent.get(1));
    }

    @Test
    void limitOfZeroReturnsEmpty() {
        repository.recordUsage("user-1", "skill-1");

        List<String> recent = repository.getRecentSkillIds("user-1", 0);

        assertTrue(recent.isEmpty());
    }
}
