package com.yeahmobi.everything.opsos.notify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.common.NetworkException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Feishu app bot channel for notification hub.
 * Sends messages directly to individuals via the Feishu Open API ({@code im/v1/messages}).
 */
public class FeishuNotifyChannel implements NotifyChannel {

    private static final Logger log = LoggerFactory.getLogger(FeishuNotifyChannel.class);
    private static final String FEISHU_APP_ACCESS_TOKEN_URL =
            "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";
    private static final String FEISHU_MESSAGE_URL =
            "https://open.feishu.cn/open-apis/im/v1/messages";

    private final HttpClientUtil httpClient;
    private final String adminUserId;
    private final String adminUserIdType;
    private final String appId;
    private final String appSecret;

    public FeishuNotifyChannel(Config config) {
        this.httpClient = new HttpClientUtil();
        this.adminUserId = config.getFeishuAdminUserId();
        this.adminUserIdType = config.getFeishuAdminUserIdType();
        this.appId = config.getFeishuOAuthAppId();
        this.appSecret = config.getFeishuOAuthAppSecret();
    }

    FeishuNotifyChannel(HttpClientUtil httpClient, String adminUserId,
                        String adminUserIdType, String appId, String appSecret) {
        this.httpClient = httpClient;
        this.adminUserId = adminUserId;
        this.adminUserIdType = adminUserIdType != null ? adminUserIdType : "union_id";
        this.appId = appId;
        this.appSecret = appSecret;
    }

    @Override
    public NotifyChannelType type() {
        return NotifyChannelType.FEISHU;
    }

    @Override
    public NotifyResult send(String target, NotifyMessage message) {
        String receiveId = (target != null && !target.isBlank()) ? target : adminUserId;
        if (receiveId == null || receiveId.isBlank()) {
            return new NotifyResult(type(), false, "飞书接收用户 ID 未配置");
        }
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            return new NotifyResult(type(), false, "飞书 app_id/app_secret 未配置");
        }
        try {
            String token = requestAppAccessToken();
            if (token == null) {
                return new NotifyResult(type(), false, "获取飞书 app_access_token 失败");
            }

            JsonObject card = new JsonObject();
            JsonObject header = new JsonObject();
            JsonObject title = new JsonObject();
            title.addProperty("tag", "plain_text");
            title.addProperty("content", safe(message == null ? "" : message.title()));
            header.add("title", title);
            card.add("header", header);
            com.google.gson.JsonArray elements = new com.google.gson.JsonArray();
            elements.add(div(message == null ? "" : message.summary()));
            elements.add(div(message == null ? "" : message.detailText()));
            card.add("elements", elements);

            JsonObject body = new JsonObject();
            body.addProperty("receive_id", receiveId);
            body.addProperty("msg_type", "interactive");
            body.addProperty("content", card.toString());

            String url = FEISHU_MESSAGE_URL + "?receive_id_type=" + adminUserIdType;
            httpClient.post(url, body.toString(), Map.of("Authorization", "Bearer " + token));
            return new NotifyResult(type(), true, "发送成功");
        } catch (NetworkException e) {
            log.warn("Feishu notify failed", e);
            return new NotifyResult(type(), false, e.getMessage() == null ? "发送失败" : e.getMessage());
        }
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

    private JsonObject div(String content) {
        JsonObject div = new JsonObject();
        div.addProperty("tag", "div");
        JsonObject text = new JsonObject();
        text.addProperty("tag", "plain_text");
        text.addProperty("content", safe(content));
        div.add("text", text);
        return div;
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        if (text.length() > 480) {
            return text.substring(0, 480);
        }
        return text;
    }
}
