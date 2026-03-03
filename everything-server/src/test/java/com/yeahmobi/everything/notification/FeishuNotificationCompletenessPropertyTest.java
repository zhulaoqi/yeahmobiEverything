package com.yeahmobi.everything.notification;

import com.yeahmobi.everything.feedback.Feedback;

import com.yeahmobi.everything.common.HttpClientUtil;
import net.jqwik.api.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for Feishu notification message completeness.
 *
 * <p>Feature: yeahmobi-everything, Property 10: 飞书通知消息完整性</p>
 *
 * <p><b>Validates: Requirements 4.3, 4.6</b></p>
 */
class FeishuNotificationCompletenessPropertyTest {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final FeishuNotifierImpl notifier =
            new FeishuNotifierImpl(new HttpClientUtil(), null, "union_id", null, null);

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 10: 飞书通知消息完整性
    void cardMessageContainsAllFeedbackFields(@ForAll("feedbacks") Feedback feedback) {
        // **Validates: Requirements 4.3, 4.6**
        String cardJson = notifier.buildCardContent(feedback);

        String expectedTime = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(feedback.timestamp()));

        assertTrue(cardJson.contains(feedback.username()),
                "Card message should contain username: " + feedback.username());
        assertTrue(cardJson.contains(expectedTime),
                "Card message should contain formatted timestamp: " + expectedTime);
        assertTrue(cardJson.contains(feedback.content()),
                "Card message should contain feedback content: " + feedback.content());
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<Feedback> feedbacks() {
        Arbitrary<String> ids = Arbitraries.strings().ofMinLength(1).ofMaxLength(36).alpha();
        Arbitrary<String> userIds = Arbitraries.strings().ofMinLength(1).ofMaxLength(36).alpha().numeric();
        Arbitrary<String> usernames = Arbitraries.strings().ofMinLength(1).ofMaxLength(30).alpha();
        Arbitrary<String> contents = Arbitraries.strings().ofMinLength(1).ofMaxLength(100).alpha();
        // Timestamps between 2020-01-01 and 2026-01-01
        Arbitrary<Long> timestamps = Arbitraries.longs().between(1577836800000L, 1767225600000L);
        Arbitrary<String> statuses = Arbitraries.of("pending", "processed");

        return Combinators.combine(ids, userIds, usernames, contents, timestamps, statuses)
                .as(Feedback::new);
    }
}
