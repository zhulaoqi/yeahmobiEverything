package com.yeahmobi.everything.notification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.common.NetworkException;
import com.yeahmobi.everything.feedback.Feedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FeishuNotifierImpl}.
 * Uses Mockito to mock HttpClientUtil.
 */
class FeishuNotifierImplTest {

    private HttpClientUtil httpClient;
    private FeishuNotifierImpl notifier;
    private FeishuNotifierImpl cardOnlyNotifier;

    private static final String ADMIN_USER_ID = "test-admin-user-id";
    private static final String ADMIN_USER_ID_TYPE = "union_id";
    private static final String APP_ID = "cli_test_app_id";
    private static final String APP_SECRET = "test_app_secret";
    private static final String TOKEN_RESPONSE = "{\"code\":0,\"data\":{\"app_access_token\":\"test-token\"}}";

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClientUtil.class);
        notifier = new FeishuNotifierImpl(httpClient,
                ADMIN_USER_ID, ADMIN_USER_ID_TYPE, APP_ID, APP_SECRET);
        cardOnlyNotifier = new FeishuNotifierImpl(httpClient,
                null, "union_id", null, null);
    }

    // --- Successful notification ---

    @Test
    void sendFeedbackNotificationReturnsTrue() throws Exception {
        Feedback feedback = new Feedback("fb-1", "u-1", "张三", "需要翻译助手", 1700000000000L, "pending");
        when(httpClient.post(anyString(), anyString(), any())).thenReturn(TOKEN_RESPONSE);

        boolean result = notifier.sendFeedbackNotification(feedback);

        assertTrue(result);
    }

    @Test
    void sendFeedbackNotificationSendsCorrectCardFormat() throws Exception {
        Feedback feedback = new Feedback("fb-1", "u-1", "张三", "需要翻译助手", 1700000000000L, "pending");
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), anyString(), any())).thenReturn(TOKEN_RESPONSE);

        notifier.sendFeedbackNotification(feedback);

        verify(httpClient, times(2)).post(urlCaptor.capture(), bodyCaptor.capture(), any());
        String messageUrl = urlCaptor.getAllValues().get(1);
        assertTrue(messageUrl.contains("im/v1/messages"));
        assertTrue(messageUrl.contains("receive_id_type=" + ADMIN_USER_ID_TYPE));

        String body = bodyCaptor.getAllValues().get(1);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();

        assertEquals("interactive", root.get("msg_type").getAsString());
        assertEquals(ADMIN_USER_ID, root.get("receive_id").getAsString());

        JsonObject card = JsonParser.parseString(root.get("content").getAsString()).getAsJsonObject();
        JsonObject header = card.getAsJsonObject("header");
        assertNotNull(header);
        JsonObject title = header.getAsJsonObject("title");
        assertEquals("plain_text", title.get("tag").getAsString());
        assertEquals("新用户反馈", title.get("content").getAsString());

        JsonArray elements = card.getAsJsonArray("elements");
        assertEquals(4, elements.size());
    }

    @Test
    void sendFeedbackNotificationIncludesUsername() throws Exception {
        Feedback feedback = new Feedback("fb-1", "u-1", "李四", "内容", 1700000000000L, "pending");
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), bodyCaptor.capture(), any())).thenReturn(TOKEN_RESPONSE);

        notifier.sendFeedbackNotification(feedback);

        String body = bodyCaptor.getAllValues().get(1);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject card = JsonParser.parseString(root.get("content").getAsString()).getAsJsonObject();
        JsonArray elements = card.getAsJsonArray("elements");

        String userElement = elements.get(0).getAsJsonObject()
                .getAsJsonObject("text").get("content").getAsString();
        assertEquals("用户: 李四", userElement);
    }

    @Test
    void sendFeedbackNotificationIncludesTimestamp() throws Exception {
        Feedback feedback = new Feedback("fb-1", "u-1", "用户", "内容", 1700000000000L, "pending");
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), bodyCaptor.capture(), any())).thenReturn(TOKEN_RESPONSE);

        notifier.sendFeedbackNotification(feedback);

        String body = bodyCaptor.getAllValues().get(1);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject card = JsonParser.parseString(root.get("content").getAsString()).getAsJsonObject();
        JsonArray elements = card.getAsJsonArray("elements");

        String timeElement = elements.get(2).getAsJsonObject()
                .getAsJsonObject("text").get("content").getAsString();
        assertTrue(timeElement.startsWith("时间: "));
        assertTrue(timeElement.length() > "时间: ".length());
    }

    @Test
    void sendFeedbackNotificationIncludesContent() throws Exception {
        Feedback feedback = new Feedback("fb-1", "u-1", "用户", "需要一个代码助手", 1700000000000L, "pending");
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), bodyCaptor.capture(), any())).thenReturn(TOKEN_RESPONSE);

        notifier.sendFeedbackNotification(feedback);

        String body = bodyCaptor.getAllValues().get(1);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject card = JsonParser.parseString(root.get("content").getAsString()).getAsJsonObject();
        JsonArray elements = card.getAsJsonArray("elements");

        String contentElement = elements.get(3).getAsJsonObject()
                .getAsJsonObject("text").get("content").getAsString();
        assertEquals("内容: 需要一个代码助手", contentElement);
    }

    @Test
    void sendFeedbackNotificationElementsUsePlainTextTag() throws Exception {
        Feedback feedback = new Feedback("fb-1", "u-1", "用户", "内容", 1700000000000L, "pending");
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClient.post(anyString(), bodyCaptor.capture(), any())).thenReturn(TOKEN_RESPONSE);

        notifier.sendFeedbackNotification(feedback);

        String body = bodyCaptor.getAllValues().get(1);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject card = JsonParser.parseString(root.get("content").getAsString()).getAsJsonObject();
        JsonArray elements = card.getAsJsonArray("elements");

        for (int i = 0; i < elements.size(); i++) {
            JsonObject element = elements.get(i).getAsJsonObject();
            assertEquals("div", element.get("tag").getAsString());
            assertEquals("plain_text", element.getAsJsonObject("text").get("tag").getAsString());
        }
    }

    // --- Failure scenarios ---

    @Test
    void sendFeedbackNotificationReturnsFalseOnNetworkError() throws Exception {
        Feedback feedback = new Feedback("fb-1", "u-1", "用户", "内容", 1700000000000L, "pending");
        when(httpClient.post(anyString(), anyString(), any()))
                .thenReturn(TOKEN_RESPONSE)
                .thenThrow(new NetworkException("Connection refused"));

        boolean result = notifier.sendFeedbackNotification(feedback);

        assertFalse(result);
    }

    @Test
    void sendFeedbackNotificationReturnsFalseWhenAdminUserIdIsNull() {
        FeishuNotifierImpl notifierNoAdmin = new FeishuNotifierImpl(httpClient,
                null, "union_id", null, null);
        Feedback feedback = new Feedback("fb-1", "u-1", "用户", "内容", 1700000000000L, "pending");

        boolean result = notifierNoAdmin.sendFeedbackNotification(feedback);

        assertFalse(result);
    }

    @Test
    void sendFeedbackNotificationReturnsFalseWhenAppCredentialsMissing() {
        FeishuNotifierImpl notifierNoApp = new FeishuNotifierImpl(
                httpClient, ADMIN_USER_ID, ADMIN_USER_ID_TYPE, null, null);
        Feedback feedback = new Feedback("fb-1", "u-1", "用户", "内容", 1700000000000L, "pending");

        boolean result = notifierNoApp.sendFeedbackNotification(feedback);

        assertFalse(result);
    }

    // --- buildCardContent tests ---

    @Test
    void buildCardContentContainsAllRequiredFields() {
        Feedback feedback = new Feedback("fb-1", "u-1", "王五", "希望增加写作助手", 1700000000000L, "pending");

        String json = cardOnlyNotifier.buildCardContent(feedback);

        assertTrue(json.contains("王五"));
        assertTrue(json.contains("希望增加写作助手"));
        assertTrue(json.contains("新用户反馈"));
    }
}
