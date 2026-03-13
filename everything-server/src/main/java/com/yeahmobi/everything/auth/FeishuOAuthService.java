package com.yeahmobi.everything.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.common.NetworkException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for Feishu OAuth 2.0 authentication.
 * <p>
 * Handles generating the authorization URL, exchanging an authorization code
 * for an access token, and retrieving user information from Feishu.
 * </p>
 */
public class FeishuOAuthService {

    private static final Logger log = LoggerFactory.getLogger(FeishuOAuthService.class);

    /** Feishu OAuth authorization endpoint */
    static final String FEISHU_AUTHORIZE_URL = "https://open.feishu.cn/open-apis/authen/v1/authorize";

    /** Feishu OAuth token endpoint */
    static final String FEISHU_TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token";

    /** Feishu app access token endpoint (needed to get user token) */
    static final String FEISHU_APP_ACCESS_TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";

    /** Feishu user info endpoint */
    static final String FEISHU_USER_INFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";

    private final Config config;
    private final HttpClientUtil httpClient;

    /**
     * Creates a FeishuOAuthService with the required dependencies.
     *
     * @param config     the application configuration
     * @param httpClient the HTTP client utility
     */
    public FeishuOAuthService(Config config, HttpClientUtil httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    /**
     * Builds the Feishu OAuth authorization URL.
     * <p>
     * The URL includes the app_id, redirect_uri, and a random state parameter
     * for CSRF protection.
     * </p>
     *
     * @return the full Feishu OAuth authorization URL
     */
    public String getAuthorizationUrl() {
        String appId = config.getFeishuOAuthAppId();
        String redirectUri = config.getFeishuOAuthRedirectUri();
        String state = UUID.randomUUID().toString();

        return FEISHU_AUTHORIZE_URL
                + "?app_id=" + encode(appId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&state=" + encode(state);
    }

    /**
     * Exchanges an authorization code for an access token.
     * <p>
     * First obtains an app_access_token, then uses it to exchange the
     * authorization code for a user access_token.
     * </p>
     *
     * @param code the authorization code from the OAuth callback
     * @return the user access token, or null if the exchange fails
     */
    public String exchangeCodeForToken(String code) {
        if (code == null || code.isBlank()) {
            log.warn("Authorization code is null or blank");
            return null;
        }

        try {
            // Step 1: Get app_access_token
            log.info("Step 1: Requesting app_access_token from Feishu");
            String appAccessToken = getAppAccessToken();
            if (appAccessToken == null) {
                log.warn("Failed to obtain app_access_token - check app_id and app_secret in config");
                return null;
            }
            log.info("Step 1: Successfully obtained app_access_token");

            // Step 2: Exchange code for user access_token
            log.info("Step 2: Exchanging authorization code for user access_token");
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("grant_type", "authorization_code");
            requestBody.addProperty("code", code);

            Map<String, String> headers = Map.of(
                    "Authorization", "Bearer " + appAccessToken
            );

            String response = httpClient.post(FEISHU_TOKEN_URL, requestBody.toString(), headers);
            log.debug("Feishu token exchange response: {}", response);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            int responseCode = json.has("code") ? json.get("code").getAsInt() : -1;
            if (responseCode != 0) {
                String msg = json.has("msg") ? json.get("msg").getAsString() : "unknown error";
                log.warn("Feishu token exchange failed: code={}, msg={}, response={}", responseCode, msg, response);
                return null;
            }

            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : null;
            if (data == null || !data.has("access_token")) {
                log.warn("Feishu token response missing data.access_token: {}", response);
                return null;
            }

            log.info("Step 2: Successfully obtained user access_token");
            return data.get("access_token").getAsString();

        } catch (NetworkException e) {
            log.warn("Network error during Feishu token exchange", e);
            return null;
        } catch (Exception e) {
            log.warn("Unexpected error during Feishu token exchange", e);
            return null;
        }
    }

    /**
     * Retrieves user information from Feishu using the access token.
     *
     * @param accessToken the user access token
     * @return a FeishuUserInfo containing user_id, name, and email; or null on failure
     */
    public FeishuUserInfo getUserInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("Access token is null or blank");
            return null;
        }

        try {
            log.info("Step 3: Fetching user info from Feishu");
            Map<String, String> headers = Map.of(
                    "Authorization", "Bearer " + accessToken
            );

            String response = httpClient.get(FEISHU_USER_INFO_URL, headers);
            log.debug("Feishu user info response: {}", response);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            int responseCode = json.has("code") ? json.get("code").getAsInt() : -1;
            if (responseCode != 0) {
                String msg = json.has("msg") ? json.get("msg").getAsString() : "unknown error";
                log.warn("Feishu user info request failed: code={}, msg={}, response={}", responseCode, msg, response);
                return null;
            }

            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : null;
            if (data == null) {
                log.warn("Feishu user info response missing data: {}", response);
                return null;
            }

            // 飞书返回的用户标识字段：优先使用 union_id（跨应用通用），否则使用 open_id（应用内唯一）
            String userId = "";
            String userIdType = "";
            if (data.has("union_id") && !data.get("union_id").isJsonNull()) {
                userId = data.get("union_id").getAsString();
                userIdType = "union_id";
            } else if (data.has("open_id") && !data.get("open_id").isJsonNull()) {
                userId = data.get("open_id").getAsString();
                userIdType = "open_id";
            } else if (data.has("user_id") && !data.get("user_id").isJsonNull()) {
                // Some Feishu responses use user_id directly
                userId = data.get("user_id").getAsString();
                userIdType = "user_id";
            }
            
            // 用户名称
            String name = data.has("name") ? data.get("name").getAsString() : "";
            
            // 邮箱：优先使用 enterprise_email（企业邮箱），否则使用 email
            String email = "";
            String emailType = "";
            if (data.has("enterprise_email") && !data.get("enterprise_email").isJsonNull()) {
                email = data.get("enterprise_email").getAsString();
                emailType = "enterprise_email";
            } else if (data.has("email") && !data.get("email").isJsonNull()) {
                email = data.get("email").getAsString();
                emailType = "email";
            }

            if (userId.isBlank()) {
                log.warn("Feishu user identifier (union_id/open_id) is empty in response: {}", response);
            }

            log.info("Step 3: Successfully fetched user info - userId={} (from {}), name={}, email={} (from {})",
                    userId, userIdType, name, email, emailType);

            return new FeishuUserInfo(userId, name, email);

        } catch (NetworkException e) {
            log.warn("Network error during Feishu user info request", e);
            return null;
        } catch (Exception e) {
            log.warn("Unexpected error during Feishu user info request", e);
            return null;
        }
    }

    // ---- Internal helpers ----

    /**
     * Obtains an app_access_token from Feishu using app_id and app_secret.
     *
     * @return the app_access_token, or null on failure
     */
    String getAppAccessToken() throws NetworkException {
        String appId = config.getFeishuOAuthAppId();
        String appSecret = config.getFeishuOAuthAppSecret();

        log.info("Requesting app_access_token with app_id={}", appId);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("app_id", appId);
        requestBody.addProperty("app_secret", appSecret);

        String response = httpClient.post(FEISHU_APP_ACCESS_TOKEN_URL, requestBody.toString(), null);
        log.debug("App access token response: {}", response);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        int responseCode = json.has("code") ? json.get("code").getAsInt() : -1;
        if (responseCode != 0) {
            String msg = json.has("msg") ? json.get("msg").getAsString() : "unknown error";
            log.warn("Failed to get app_access_token: code={}, msg={}, response={}", responseCode, msg, response);
            return null;
        }

        if (!json.has("app_access_token")) {
            log.warn("Response missing app_access_token field: {}", response);
            return null;
        }

        log.info("Successfully obtained app_access_token");
        return json.get("app_access_token").getAsString();
    }

    /**
     * URL-encodes a string value.
     */
    static String encode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Represents user information retrieved from Feishu.
     *
     * @param userId the Feishu user identifier (union_id preferred, or open_id)
     * @param name   the user's display name
     * @param email  the user's email address (enterprise_email preferred, or email)
     */
    public record FeishuUserInfo(String userId, String name, String email) {}
}
