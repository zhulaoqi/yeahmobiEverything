package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.feedback.Feedback;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for feedback list time ordering.
 *
 * <p>Feature: yeahmobi-everything, Property 13: 反馈列表时间倒序</p>
 */
class FeedbackListOrderPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 13: 反馈列表时间倒序
    void getAllFeedbacks_orderedByTimestampDescending(
            @ForAll("feedbackLists") List<Feedback> feedbacks) {
        // **Validates: Requirements 5.5**

        // Simulate the repository returning feedbacks in descending order (as the SQL does)
        List<Feedback> sorted = new ArrayList<>(feedbacks);
        sorted.sort(Comparator.comparingLong(Feedback::timestamp).reversed());

        SkillRepository skillRepo = mock(SkillRepository.class);
        FeedbackRepository feedbackRepo = mock(FeedbackRepository.class);
        CacheService cacheService = mock(CacheService.class);
        when(feedbackRepo.getAllFeedbacks()).thenReturn(sorted);

        AdminServiceImpl service = new AdminServiceImpl(skillRepo, feedbackRepo, cacheService);
        List<Feedback> result = service.getAllFeedbacks();

        // Verify descending order: each adjacent pair satisfies prev.timestamp >= next.timestamp
        for (int i = 0; i < result.size() - 1; i++) {
            assertTrue(result.get(i).timestamp() >= result.get(i + 1).timestamp(),
                    "Feedback at index " + i + " (ts=" + result.get(i).timestamp()
                            + ") should have timestamp >= feedback at index " + (i + 1)
                            + " (ts=" + result.get(i + 1).timestamp() + ")");
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<List<Feedback>> feedbackLists() {
        return feedbacks().list().ofMinSize(0).ofMaxSize(20);
    }

    @Provide
    Arbitrary<Feedback> feedbacks() {
        Arbitrary<String> ids = Arbitraries.strings().ofMinLength(5).ofMaxLength(10).alpha();
        Arbitrary<String> userIds = Arbitraries.of("u1", "u2", "u3", "u4");
        Arbitrary<String> usernames = Arbitraries.of("alice", "bob", "charlie", "dave");
        Arbitrary<String> contents = Arbitraries.strings().ofMinLength(1).ofMaxLength(30).alpha();
        Arbitrary<Long> timestamps = Arbitraries.longs().between(1_000_000_000_000L, 2_000_000_000_000L);
        Arbitrary<String> statuses = Arbitraries.of("pending", "processed");

        return Combinators.combine(ids, userIds, usernames, contents, timestamps, statuses)
                .as(Feedback::new);
    }
}
