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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for Feishu OAuth 2.0 authentication.
 * <p>
 * Handles generating the authorization URL, exchanging an authorization code
 * for an access token, and retrieving user information from Feishu.
 * </p>
 */
public class FeishuOAuthService {

    private static final Logger LOGGER = Logger.getLogger(FeishuOAuthService.class.getName());

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
            LOGGER.warning("Authorization code is null or blank");
            return null;
        }

        try {
            // Step 1: Get app_access_token
            LOGGER.info("Step 1: Requesting app_access_token from Feishu");
            String appAccessToken = getAppAccessToken();
            if (appAccessToken == null) {
                LOGGER.warning("Failed to obtain app_access_token - check app_id and app_secret in config");
                return null;
            }
            LOGGER.info("Step 1: Successfully obtained app_access_token");

            // Step 2: Exchange code for user access_token
            LOGGER.info("Step 2: Exchanging authorization code for user access_token");
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("grant_type", "authorization_code");
            requestBody.addProperty("code", code);

            Map<String, String> headers = Map.of(
                    "Authorization", "Bearer " + appAccessToken
            );

            String response = httpClient.post(FEISHU_TOKEN_URL, requestBody.toString(), headers);
            LOGGER.log(Level.FINE, "Feishu token exchange response: {0}", response);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            int responseCode = json.has("code") ? json.get("code").getAsInt() : -1;
            if (responseCode != 0) {
                String msg = json.has("msg") ? json.get("msg").getAsString() : "unknown error";
                LOGGER.log(Level.WARNING, "Feishu token exchange failed: code={0}, msg={1}, response={2}",
                        new Object[]{responseCode, msg, response});
                return null;
            }

            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : null;
            if (data == null || !data.has("access_token")) {
                LOGGER.log(Level.WARNING, "Feishu token response missing data.access_token: {0}", response);
                return null;
            }

            LOGGER.info("Step 2: Successfully obtained user access_token");
            return data.get("access_token").getAsString();

        } catch (NetworkException e) {
            LOGGER.log(Level.WARNING, "Network error during Feishu token exchange: " + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unexpected error during Feishu token exchange: " + e.getMessage(), e);
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
            LOGGER.warning("Access token is null or blank");
            return null;
        }

        try {
            LOGGER.info("Step 3: Fetching user info from Feishu");
            Map<String, String> headers = Map.of(
                    "Authorization", "Bearer " + accessToken
            );

            String response = httpClient.get(FEISHU_USER_INFO_URL, headers);
            LOGGER.log(Level.FINE, "Feishu user info response: {0}", response);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            int responseCode = json.has("code") ? json.get("code").getAsInt() : -1;
            if (responseCode != 0) {
                String msg = json.has("msg") ? json.get("msg").getAsString() : "unknown error";
                LOGGER.log(Level.WARNING, "Feishu user info request failed: code={0}, msg={1}, response={2}",
                        new Object[]{responseCode, msg, response});
                return null;
            }

            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : null;
            if (data == null) {
                LOGGER.log(Level.WARNING, "Feishu user info response missing data: {0}", response);
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
                LOGGER.log(Level.WARNING, "Feishu user identifier (union_id/open_id) is empty in response: {0}", response);
            }

            LOGGER.log(Level.INFO, "Step 3: Successfully fetched user info - userId={0} (from {1}), name={2}, email={3} (from {4})",
                    new Object[]{userId, userIdType, name, email, emailType});

            return new FeishuUserInfo(userId, name, email);

        } catch (NetworkException e) {
            LOGGER.log(Level.WARNING, "Network error during Feishu user info request: " + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unexpected error during Feishu user info request: " + e.getMessage(), e);
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

        LOGGER.log(Level.INFO, "Requesting app_access_token with app_id={0}", appId);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("app_id", appId);
        requestBody.addProperty("app_secret", appSecret);

        String response = httpClient.post(FEISHU_APP_ACCESS_TOKEN_URL, requestBody.toString(), null);
        LOGGER.log(Level.FINE, "App access token response: {0}", response);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        int responseCode = json.has("code") ? json.get("code").getAsInt() : -1;
        if (responseCode != 0) {
            String msg = json.has("msg") ? json.get("msg").getAsString() : "unknown error";
            LOGGER.log(Level.WARNING, "Failed to get app_access_token: code={0}, msg={1}, response={2}",
                    new Object[]{responseCode, msg, response});
            return null;
        }

        if (!json.has("app_access_token")) {
            LOGGER.log(Level.WARNING, "Response missing app_access_token field: {0}", response);
            return null;
        }

        LOGGER.info("Successfully obtained app_access_token");
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
