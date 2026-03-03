package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.feedback.Feedback;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import net.jqwik.api.*;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for feedback status update.
 *
 * <p>Feature: yeahmobi-everything, Property 14: 反馈状态更新</p>
 */
class FeedbackStatusUpdatePropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 14: 反馈状态更新
    void markFeedbackProcessed_updatesStatusAndTimestamp(
            @ForAll("feedbacks") Feedback feedback) {
        // **Validates: Requirements 5.6**

        AtomicReference<String> capturedStatus = new AtomicReference<>();
        AtomicLong capturedProcessedAt = new AtomicLong();
        AtomicReference<String> capturedId = new AtomicReference<>();

        SkillRepository skillRepo = mock(SkillRepository.class);
        FeedbackRepository feedbackRepo = mock(FeedbackRepository.class);
        CacheService cacheService = mock(CacheService.class);

        doAnswer(inv -> {
            capturedId.set(inv.getArgument(0));
            capturedStatus.set(inv.getArgument(1));
            capturedProcessedAt.set(inv.getArgument(2));
            return null;
        }).when(feedbackRepo).updateFeedbackStatus(anyString(), anyString(), anyLong());

        AdminServiceImpl service = new AdminServiceImpl(skillRepo, feedbackRepo, cacheService);

        long beforeCall = System.currentTimeMillis();
        service.markFeedbackProcessed(feedback.id());
        long afterCall = System.currentTimeMillis();

        // Verify the correct feedback ID was passed
        assertEquals(feedback.id(), capturedId.get(), "Should update the correct feedback ID");

        // Verify status is "processed"
        assertEquals("processed", capturedStatus.get(), "Status should be 'processed'");

        // Verify processedAt timestamp is reasonable (between before and after the call)
        assertTrue(capturedProcessedAt.get() >= beforeCall,
                "processedAt should be >= time before call");
        assertTrue(capturedProcessedAt.get() <= afterCall,
                "processedAt should be <= time after call");
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<Feedback> feedbacks() {
        Arbitrary<String> ids = Arbitraries.strings().ofMinLength(5).ofMaxLength(10).alpha();
        Arbitrary<String> userIds = Arbitraries.of("u1", "u2", "u3", "u4");
        Arbitrary<String> usernames = Arbitraries.of("alice", "bob", "charlie", "dave");
        Arbitrary<String> contents = Arbitraries.strings().ofMinLength(1).ofMaxLength(30).alpha();
        Arbitrary<Long> timestamps = Arbitraries.longs().between(1_000_000_000_000L, 2_000_000_000_000L);

        return Combinators.combine(ids, userIds, usernames, contents, timestamps)
                .as((id, userId, user, content, ts) -> new Feedback(id, userId, user, content, ts, "pending"));
    }
}
