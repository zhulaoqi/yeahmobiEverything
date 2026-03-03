package com.yeahmobi.everything.repository.local;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for UsageRepository recently-used ordering.
 *
 * <p>Feature: yeahmobi-everything, Property 7: 最近使用排序</p>
 *
 * <p><b>Validates: Requirements 2.6</b></p>
 *
 * <p>Uses a real SQLite in-memory database (no mocks) to verify that for any
 * sequence of skill usage records, getRecentSkillIds returns a list ordered
 * by most recent usage time (descending) and respects the specified limit.</p>
 */
class UsageRepositoryPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 7: 最近使用排序
    void recentlyUsedOrdering(
            @ForAll("usageSequences") List<String> skillIdSequence,
            @ForAll @IntRange(min = 1, max = 20) int limit
    ) throws SQLException, IOException {
        // **Validates: Requirements 2.6**

        // Arrange: fresh in-memory SQLite database for each trial
        LocalDatabaseManager databaseManager = new LocalDatabaseManager(":memory:");
        try {
            databaseManager.initialize();
            UsageRepositoryImpl repository = new UsageRepositoryImpl(databaseManager);

            String userId = "test-user";

            // Insert usage records with controlled sequential timestamps
            // to avoid timing issues with System.currentTimeMillis()
            long baseTimestamp = 1000000L;
            Connection conn = databaseManager.getConnection();
            for (int i = 0; i < skillIdSequence.size(); i++) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO skill_usage (user_id, skill_id, used_at) VALUES (?, ?, ?)")) {
                    stmt.setString(1, userId);
                    stmt.setString(2, skillIdSequence.get(i));
                    stmt.setLong(3, baseTimestamp + i);
                    stmt.executeUpdate();
                }
            }

            // Act: get recently used skill IDs
            List<String> result = repository.getRecentSkillIds(userId, limit);

            // Compute expected ordering: for each distinct skill, find its MAX timestamp
            // then sort by MAX timestamp descending
            Map<String, Long> maxTimestampBySkill = new LinkedHashMap<>();
            for (int i = 0; i < skillIdSequence.size(); i++) {
                String skillId = skillIdSequence.get(i);
                long timestamp = baseTimestamp + i;
                maxTimestampBySkill.merge(skillId, timestamp, Math::max);
            }

            // Sort distinct skills by their max timestamp descending
            List<String> expectedOrder = new ArrayList<>(maxTimestampBySkill.keySet());
            expectedOrder.sort((a, b) -> Long.compare(
                    maxTimestampBySkill.get(b), maxTimestampBySkill.get(a)));

            int expectedSize = Math.min(expectedOrder.size(), limit);

            // Assert 1: result size should not exceed the limit
            assertTrue(result.size() <= limit,
                    "Result size (" + result.size() + ") should not exceed limit (" + limit + ")");

            // Assert 2: result size should equal min(distinct skills, limit)
            assertEquals(expectedSize, result.size(),
                    "Result size should be min(distinct skills=" + expectedOrder.size()
                            + ", limit=" + limit + ")");

            // Assert 3: result should be ordered by most recent usage first
            List<String> expectedTruncated = expectedOrder.subList(0, expectedSize);
            assertEquals(expectedTruncated, result,
                    "Result should match expected order (most recently used first)");

        } finally {
            databaseManager.close();
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<List<String>> usageSequences() {
        // Generate a list of 1-30 skill IDs, drawn from a small pool
        // to ensure some duplicates (testing the MAX(used_at) grouping)
        Arbitrary<String> skillIds = Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(10)
                .alpha()
                .numeric();

        return skillIds.list().ofMinSize(1).ofMaxSize(30);
    }
}
