package com.yeahmobi.everything.notification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.common.NetworkException;
import com.yeahmobi.everything.feedback.Feedback;
import com.yeahmobi.everything.personalskill.PersonalSkill;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Implementation of {@link FeishuNotifier} that sends notifications
 * directly to individuals via Feishu app bot ({@code im/v1/messages} API).
 */
public class FeishuNotifierImpl implements FeishuNotifier {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String FEISHU_APP_ACCESS_TOKEN_URL =
            "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";
    private static final String FEISHU_MESSAGE_URL =
            "https://open.feishu.cn/open-apis/im/v1/messages";

    private final HttpClientUtil httpClient;
    private final String adminUserId;
    private final String adminUserIdType;
    private final String appId;
    private final String appSecret;

    /**
     * Creates a FeishuNotifierImpl using application configuration.
     *
     * @param config     the application configuration
     * @param httpClient the HTTP client utility for sending requests
     */
    public FeishuNotifierImpl(Config config, HttpClientUtil httpClient) {
        this.httpClient = httpClient;
        this.adminUserId = config.getFeishuAdminUserId();
        this.adminUserIdType = config.getFeishuAdminUserIdType();
        this.appId = config.getFeishuOAuthAppId();
        this.appSecret = config.getFeishuOAuthAppSecret();
    }

    /**
     * Creates a FeishuNotifierImpl with explicit parameters for testing.
     *
     * @param httpClient       the HTTP client utility for sending requests
     * @param adminUserId      the admin user ID to receive messages
     * @param adminUserIdType  the user ID type (e.g. "union_id", "open_id")
     * @param appId            the Feishu app ID
     * @param appSecret        the Feishu app secret
     */
    public FeishuNotifierImpl(HttpClientUtil httpClient,
                              String adminUserId, String adminUserIdType,
                              String appId, String appSecret) {
        this.httpClient = httpClient;
        this.adminUserId = adminUserId;
        this.adminUserIdType = adminUserIdType != null ? adminUserIdType : "union_id";
        this.appId = appId;
        this.appSecret = appSecret;
    }

    @Override
    public boolean sendFeedbackNotification(Feedback feedback) {
        if (adminUserId == null || adminUserId.isBlank()) {
            System.err.println("Feishu admin user id is not configured");
            return false;
        }
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            System.err.println("Feishu app_id/app_secret is not configured");
            return false;
        }

        try {
            String token = requestAppAccessToken();
            if (token == null) {
                return false;
            }

            String cardContent = buildCardContent(feedback);
            JsonObject body = new JsonObject();
            body.addProperty("receive_id", adminUserId);
            body.addProperty("msg_type", "interactive");
            body.addProperty("content", cardContent);

            String url = FEISHU_MESSAGE_URL + "?receive_id_type=" + adminUserIdType;
            httpClient.post(url, body.toString(), Map.of("Authorization", "Bearer " + token));
            return true;
        } catch (Exception e) {
            System.err.println("Failed to send Feishu notification: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendPersonalSkillReviewNotification(PersonalSkill skill) {
        if (adminUserId == null || adminUserId.isBlank()) {
            System.err.println("Feishu admin user id is not configured");
            return false;
        }
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            System.err.println("Feishu app_id/app_secret is not configured");
            return false;
        }

        try {
            String token = requestAppAccessToken();
            if (token == null) {
                return false;
            }

            JsonObject body = new JsonObject();
            body.addProperty("receive_id", adminUserId);
            body.addProperty("msg_type", "text");
            JsonObject content = new JsonObject();
            content.addProperty("text", buildPersonalSkillText(skill));
            body.add("content", content);

            String url = FEISHU_MESSAGE_URL + "?receive_id_type=" + adminUserIdType;
            httpClient.post(url, body.toString(), Map.of("Authorization", "Bearer " + token));
            return true;
        } catch (Exception e) {
            System.err.println("Failed to send Feishu private notification: " + e.getMessage());
            return false;
        }
    }

    /**
     * Builds the Feishu card content JSON string for a feedback.
     * Returns the card JSON (header + elements) as a string, suitable for
     * the {@code content} field of the {@code im/v1/messages} API.
     *
     * @param feedback the feedback to build the card for
     * @return the card JSON string
     */
    String buildCardContent(Feedback feedback) {
        String formattedTime = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(feedback.timestamp()));

        JsonObject card = new JsonObject();

        // Header
        JsonObject header = new JsonObject();
        JsonObject title = new JsonObject();
        title.addProperty("tag", "plain_text");
        title.addProperty("content", "新用户反馈");
        header.add("title", title);
        card.add("header", header);

        // Elements
        JsonArray elements = new JsonArray();
        elements.add(createDivElement("用户: " + feedback.username()));
        if (feedback.userId() != null && !feedback.userId().isBlank()) {
            elements.add(createDivElement("用户ID: " + feedback.userId()));
        }
        elements.add(createDivElement("时间: " + formattedTime));
        elements.add(createDivElement("内容: " + feedback.content()));
        card.add("elements", elements);

        return card.toString();
    }

    private String buildPersonalSkillText(PersonalSkill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("有新的个人 Skill 提交审核\n");
        sb.append("名称: ").append(skill.name()).append("\n");
        sb.append("分类: ").append(skill.category()).append("\n");
        sb.append("用户ID: ").append(skill.userId()).append("\n");
        sb.append("描述: ").append(skill.description()).append("\n");
        sb.append("请在管理端 > 个人 Skill 审核中处理。");
        return sb.toString();
    }

    private String requestAppAccessToken() throws NetworkException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("app_id", appId);
        requestBody.addProperty("app_secret", appSecret);

        String response = httpClient.post(FEISHU_APP_ACCESS_TOKEN_URL, requestBody.toString(), null);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        int responseCode = json.has("code") ? json.get("code").getAsInt() : -1;
        if (responseCode != 0) {
            return null;
        }
        JsonObject data = json.has("data") ? json.getAsJsonObject("data") : null;
        if (data == null || !data.has("app_access_token")) {
            return null;
        }
        return data.get("app_access_token").getAsString();
    }

    /**
     * Creates a div element for the Feishu card.
     */
    private JsonObject createDivElement(String content) {
        JsonObject div = new JsonObject();
        div.addProperty("tag", "div");

        JsonObject text = new JsonObject();
        text.addProperty("tag", "plain_text");
        text.addProperty("content", content);

        div.add("text", text);
        return div;
    }
}
