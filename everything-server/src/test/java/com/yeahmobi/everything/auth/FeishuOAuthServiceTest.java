package com.yeahmobi.everything.auth;

import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.common.NetworkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FeishuOAuthService}.
 * Uses Mockito to mock HttpClientUtil for HTTP responses.
 */
class FeishuOAuthServiceTest {

    private Config config;
    private HttpClientUtil httpClient;
    private FeishuOAuthService feishuOAuthService;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.setProperty("feishu.oauth.app_id", "test_app_id");
        props.setProperty("feishu.oauth.app_secret", "test_app_secret");
        props.setProperty("feishu.oauth.redirect_uri", "http://localhost:8080/auth/feishu/callback");
        config = Config.fromProperties(props);

        httpClient = mock(HttpClientUtil.class);
        feishuOAuthService = new FeishuOAuthService(config, httpClient);
    }

    // ---- getAuthorizationUrl tests ----

    @Test
    void getAuthorizationUrl_containsAppId() {
        String url = feishuOAuthService.getAuthorizationUrl();

        assertTrue(url.contains("app_id=test_app_id"));
    }

    @Test
    void getAuthorizationUrl_containsRedirectUri() {
        String url = feishuOAuthService.getAuthorizationUrl();

        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("localhost"));
    }

    @Test
    void getAuthorizationUrl_containsResponseType() {
        String url = feishuOAuthService.getAuthorizationUrl();

        assertTrue(url.contains("response_type=code"));
    }

    @Test
    void getAuthorizationUrl_containsState() {
        String url = feishuOAuthService.getAuthorizationUrl();

        assertTrue(url.contains("state="));
    }

    @Test
    void getAuthorizationUrl_startsWithFeishuEndpoint() {
        String url = feishuOAuthService.getAuthorizationUrl();

        assertTrue(url.startsWith(FeishuOAuthService.FEISHU_AUTHORIZE_URL));
    }

    // ---- exchangeCodeForToken tests ----

    @Test
    void exchangeCodeForToken_success_returnsAccessToken() throws NetworkException {
        // Mock app_access_token response
        String appTokenResponse = "{\"code\":0,\"app_access_token\":\"app_token_123\",\"expire\":7200}";
        when(httpClient.post(eq(FeishuOAuthService.FEISHU_APP_ACCESS_TOKEN_URL), anyString(), isNull()))
                .thenReturn(appTokenResponse);

        // Mock user token exchange response
        String tokenResponse = "{\"code\":0,\"data\":{\"access_token\":\"user_token_456\",\"token_type\":\"Bearer\",\"expires_in\":7200}}";
        when(httpClient.post(eq(FeishuOAuthService.FEISHU_TOKEN_URL), anyString(), any()))
                .thenReturn(tokenResponse);

        String token = feishuOAuthService.exchangeCodeForToken("auth_code_123");

        assertEquals("user_token_456", token);
    }

    @Test
    void exchangeCodeForToken_nullCode_returnsNull() {
        String token = feishuOAuthService.exchangeCodeForToken(null);
        assertNull(token);
    }

    @Test
    void exchangeCodeForToken_blankCode_returnsNull() {
        String token = feishuOAuthService.exchangeCodeForToken("  ");
        assertNull(token);
    }

    @Test
    void exchangeCodeForToken_appTokenFails_returnsNull() throws NetworkException {
        String appTokenResponse = "{\"code\":10003,\"msg\":\"invalid app_id\"}";
        when(httpClient.post(eq(FeishuOAuthService.FEISHU_APP_ACCESS_TOKEN_URL), anyString(), isNull()))
                .thenReturn(appTokenResponse);

        String token = feishuOAuthService.exchangeCodeForToken("auth_code");

        assertNull(token);
    }

    @Test
    void exchangeCodeForToken_tokenExchangeFails_returnsNull() throws NetworkException {
        // Mock successful app_access_token
        String appTokenResponse = "{\"code\":0,\"app_access_token\":\"app_token_123\"}";
        when(httpClient.post(eq(FeishuOAuthService.FEISHU_APP_ACCESS_TOKEN_URL), anyString(), isNull()))
                .thenReturn(appTokenResponse);

        // Mock failed token exchange
        String tokenResponse = "{\"code\":10012,\"msg\":\"invalid code\"}";
        when(httpClient.post(eq(FeishuOAuthService.FEISHU_TOKEN_URL), anyString(), any()))
                .thenReturn(tokenResponse);

        String token = feishuOAuthService.exchangeCodeForToken("bad_code");

        assertNull(token);
    }

    @Test
    void exchangeCodeForToken_networkError_returnsNull() throws NetworkException {
        when(httpClient.post(eq(FeishuOAuthService.FEISHU_APP_ACCESS_TOKEN_URL), anyString(), isNull()))
                .thenThrow(new NetworkException("Connection refused"));

        String token = feishuOAuthService.exchangeCodeForToken("auth_code");

        assertNull(token);
    }

    @Test
    void exchangeCodeForToken_missingAccessTokenInResponse_returnsNull() throws NetworkException {
        String appTokenResponse = "{\"code\":0,\"app_access_token\":\"app_token_123\"}";
        when(httpClient.post(eq(FeishuOAuthService.FEISHU_APP_ACCESS_TOKEN_URL), anyString(), isNull()))
                .thenReturn(appTokenResponse);

        // Response has code=0 but no data.access_token
        String tokenResponse = "{\"code\":0,\"data\":{}}";
        when(httpClient.post(eq(FeishuOAuthService.FEISHU_TOKEN_URL), anyString(), any()))
                .thenReturn(tokenResponse);

        String token = feishuOAuthService.exchangeCodeForToken("auth_code");

        assertNull(token);
    }

    // ---- getUserInfo tests ----

    @Test
    void getUserInfo_success_returnsUserInfo() throws NetworkException {
        String userInfoResponse = "{\"code\":0,\"data\":{\"user_id\":\"fs_123\",\"name\":\"张三\",\"email\":\"zhangsan@example.com\"}}";
        when(httpClient.get(eq(FeishuOAuthService.FEISHU_USER_INFO_URL), any()))
                .thenReturn(userInfoResponse);

        FeishuOAuthService.FeishuUserInfo userInfo = feishuOAuthService.getUserInfo("access_token");

        assertNotNull(userInfo);
        assertEquals("fs_123", userInfo.userId());
        assertEquals("张三", userInfo.name());
        assertEquals("zhangsan@example.com", userInfo.email());
    }

    @Test
    void getUserInfo_nullToken_returnsNull() {
        FeishuOAuthService.FeishuUserInfo userInfo = feishuOAuthService.getUserInfo(null);
        assertNull(userInfo);
    }

    @Test
    void getUserInfo_blankToken_returnsNull() {
        FeishuOAuthService.FeishuUserInfo userInfo = feishuOAuthService.getUserInfo("  ");
        assertNull(userInfo);
    }

    @Test
    void getUserInfo_apiError_returnsNull() throws NetworkException {
        String errorResponse = "{\"code\":10001,\"msg\":\"invalid access_token\"}";
        when(httpClient.get(eq(FeishuOAuthService.FEISHU_USER_INFO_URL), any()))
                .thenReturn(errorResponse);

        FeishuOAuthService.FeishuUserInfo userInfo = feishuOAuthService.getUserInfo("bad_token");

        assertNull(userInfo);
    }

    @Test
    void getUserInfo_networkError_returnsNull() throws NetworkException {
        when(httpClient.get(eq(FeishuOAuthService.FEISHU_USER_INFO_URL), any()))
                .thenThrow(new NetworkException("Timeout"));

        FeishuOAuthService.FeishuUserInfo userInfo = feishuOAuthService.getUserInfo("token");

        assertNull(userInfo);
    }

    @Test
    void getUserInfo_missingData_returnsNull() throws NetworkException {
        String response = "{\"code\":0}";
        when(httpClient.get(eq(FeishuOAuthService.FEISHU_USER_INFO_URL), any()))
                .thenReturn(response);

        FeishuOAuthService.FeishuUserInfo userInfo = feishuOAuthService.getUserInfo("token");

        assertNull(userInfo);
    }

    @Test
    void getUserInfo_partialData_returnsWithEmptyFields() throws NetworkException {
        // Only user_id present, name and email missing
        String response = "{\"code\":0,\"data\":{\"user_id\":\"fs_123\"}}";
        when(httpClient.get(eq(FeishuOAuthService.FEISHU_USER_INFO_URL), any()))
                .thenReturn(response);

        FeishuOAuthService.FeishuUserInfo userInfo = feishuOAuthService.getUserInfo("token");

        assertNotNull(userInfo);
        assertEquals("fs_123", userInfo.userId());
        assertEquals("", userInfo.name());
        assertEquals("", userInfo.email());
    }

    // ---- encode tests ----

    @Test
    void encode_nullValue_returnsEmptyString() {
        assertEquals("", FeishuOAuthService.encode(null));
    }

    @Test
    void encode_normalValue_returnsEncoded() {
        String encoded = FeishuOAuthService.encode("http://localhost:8080/callback");
        assertTrue(encoded.contains("localhost"));
        assertFalse(encoded.contains("://"));
    }
}
