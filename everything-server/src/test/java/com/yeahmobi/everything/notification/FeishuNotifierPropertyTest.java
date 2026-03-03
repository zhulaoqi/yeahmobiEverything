package com.yeahmobi.everything.notification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.feedback.Feedback;
import net.jqwik.api.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for FeishuNotifierImpl card message construction.
 *
 * <p>Feature: yeahmobi-everything, Property 10: 飞书通知消息完整性</p>
 *
 * <p><b>Validates: Requirements 4.3, 4.6</b></p>
 *
 * <p>For any Feedback object, FeishuNotifier's constructed Feishu message
 * should contain the feedback's username, submission time, and feedback content.</p>
 */
class FeishuNotifierPropertyTest {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final FeishuNotifierImpl notifier;

    FeishuNotifierPropertyTest() {
        HttpClientUtil httpClient = mock(HttpClientUtil.class);
        this.notifier = new FeishuNotifierImpl(httpClient, null, "union_id", null, null);
    }

    @Property(tries = 100)
    @Label("Feature: yeahmobi-everything, Property 10: 飞书通知消息完整性")
    void feishuCardMessageContainsUsernameTimestampAndContent(
            @ForAll("feedbacks") Feedback feedback
    ) {
        // **Validates: Requirements 4.3, 4.6**

        String cardJson = notifier.buildCardContent(feedback);

        JsonObject card = JsonParser.parseString(cardJson).getAsJsonObject();

        // Verify: header with "新用户反馈"
        JsonObject header = card.getAsJsonObject("header");
        assertNotNull(header, "Card must contain a header");
        JsonObject title = header.getAsJsonObject("title");
        assertEquals("新用户反馈", title.get("content").getAsString(),
                "Card header must be '新用户反馈'");

        // Verify: elements array exists with 4 elements (user + userId + time + content)
        JsonArray elements = card.getAsJsonArray("elements");
        assertNotNull(elements, "Card must contain elements array");
        assertEquals(4, elements.size(), "Card must have exactly 4 elements");

        // Verify: username is present in the first element
        String userElement = elements.get(0).getAsJsonObject()
                .getAsJsonObject("text").get("content").getAsString();
        assertTrue(userElement.contains(feedback.username()),
                "First element must contain the username '" + feedback.username() + "', got: " + userElement);

        // Verify: formatted timestamp is present in the third element
        String expectedFormattedTime = TIMESTAMP_FORMATTER.format(
                Instant.ofEpochMilli(feedback.timestamp()));
        String timeElement = elements.get(2).getAsJsonObject()
                .getAsJsonObject("text").get("content").getAsString();
        assertTrue(timeElement.contains(expectedFormattedTime),
                "Time element must contain the formatted timestamp '" + expectedFormattedTime + "', got: " + timeElement);

        // Verify: feedback content is present in the fourth element
        String contentElement = elements.get(3).getAsJsonObject()
                .getAsJsonObject("text").get("content").getAsString();
        assertTrue(contentElement.contains(feedback.content()),
                "Content element must contain the feedback content '" + feedback.content() + "', got: " + contentElement);
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<Feedback> feedbacks() {
        Arbitrary<String> ids = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(36)
                .alpha().numeric();

        Arbitrary<String> userIds = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(36)
                .alpha().numeric();

        Arbitrary<String> usernames = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(50)
                .alpha();

        Arbitrary<String> contents = Arbitraries.strings()
                .ofMinLength(1).ofMaxLength(200)
                .alpha().numeric();

        // Generate timestamps within a reasonable range (2020-01-01 to 2030-01-01)
        Arbitrary<Long> timestamps = Arbitraries.longs()
                .between(1577836800000L, 1893456000000L);

        Arbitrary<String> statuses = Arbitraries.of("pending", "processed");

        return Combinators.combine(ids, userIds, usernames, contents, timestamps, statuses)
                .as(Feedback::new);
    }
}
