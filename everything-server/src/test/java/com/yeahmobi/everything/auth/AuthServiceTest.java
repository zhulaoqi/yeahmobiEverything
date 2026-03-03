package com.yeahmobi.everything.auth;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.SessionRepository;
import com.yeahmobi.everything.repository.mysql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthServiceImpl}.
 * Uses Mockito to mock SessionRepository, UserRepository, and CacheService.
 */
class AuthServiceTest {

    private SessionRepository sessionRepository;
    private UserRepository userRepository;
    private CacheService cacheService;
    private FeishuOAuthService feishuOAuthService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        userRepository = mock(UserRepository.class);
        cacheService = mock(CacheService.class);
        feishuOAuthService = mock(FeishuOAuthService.class);
        authService = new AuthServiceImpl(sessionRepository, userRepository, cacheService, feishuOAuthService);
    }

    // ---- loginWithEmail tests ----

    @Test
    void loginWithEmail_validCredentials_returnsSuccess() {
        String email = "user@example.com";
        String password = "secret123";
        String passwordHash = AuthServiceImpl.hashPassword(password);
        User user = new User("u1", email, passwordHash, "user", null, "email", false, System.currentTimeMillis());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthResult result = authService.loginWithEmail(email, password);

        assertTrue(result.success());
        assertEquals("登录成功", result.message());
        assertNotNull(result.session());
        assertEquals("u1", result.session().userId());
        assertEquals(email, result.session().email());
        assertEquals("email", result.session().loginType());

        // Verify session was saved locally and cached
        verify(sessionRepository).saveSession(any(Session.class));
        verify(cacheService).cacheSession(anyString(), any(Session.class), eq(AuthServiceImpl.SESSION_TTL_SECONDS));
    }

    @Test
    void loginWithEmail_wrongPassword_returnsFail() {
        String email = "user@example.com";
        String correctHash = AuthServiceImpl.hashPassword("correct");
        User user = new User("u1", email, correctHash, "user", null, "email", false, System.currentTimeMillis());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthResult result = authService.loginWithEmail(email, "wrong");

        assertFalse(result.success());
        assertNull(result.session());
        verify(sessionRepository, never()).saveSession(any());
    }

    @Test
    void loginWithEmail_userNotFound_returnsFail() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        AuthResult result = authService.loginWithEmail("unknown@example.com", "password");

        assertFalse(result.success());
        assertNull(result.session());
    }

    @Test
    void loginWithEmail_nullEmail_returnsFail() {
        AuthResult result = authService.loginWithEmail(null, "password");
        assertFalse(result.success());
        assertEquals("邮箱不能为空", result.message());
    }

    @Test
    void loginWithEmail_emptyEmail_returnsFail() {
        AuthResult result = authService.loginWithEmail("  ", "password");
        assertFalse(result.success());
        assertEquals("邮箱不能为空", result.message());
    }

    @Test
    void loginWithEmail_nullPassword_returnsFail() {
        AuthResult result = authService.loginWithEmail("user@example.com", null);
        assertFalse(result.success());
        assertEquals("密码不能为空", result.message());
    }

    @Test
    void loginWithEmail_emptyPassword_returnsFail() {
        AuthResult result = authService.loginWithEmail("user@example.com", "");
        assertFalse(result.success());
        assertEquals("密码不能为空", result.message());
    }

    // ---- sendVerificationCode tests ----

    @Test
    void sendVerificationCode_validEmail_returnsTrue() {
        boolean result = authService.sendVerificationCode("user@example.com");

        assertTrue(result);
        assertTrue(authService.getVerificationCodes().containsKey("user@example.com"));
        AuthServiceImpl.VerificationEntry entry = authService.getVerificationCodes().get("user@example.com");
        assertNotNull(entry);
        assertEquals(6, entry.code().length());
        assertTrue(entry.expiresAt() > System.currentTimeMillis());
    }

    @Test
    void sendVerificationCode_nullEmail_returnsFalse() {
        assertFalse(authService.sendVerificationCode(null));
    }

    @Test
    void sendVerificationCode_emptyEmail_returnsFalse() {
        assertFalse(authService.sendVerificationCode("  "));
    }

    @Test
    void sendVerificationCode_overwritesPreviousCode() {
        authService.sendVerificationCode("user@example.com");
        String firstCode = authService.getVerificationCodes().get("user@example.com").code();

        authService.sendVerificationCode("user@example.com");
        String secondCode = authService.getVerificationCodes().get("user@example.com").code();

        // The map should have exactly one entry (overwritten)
        assertEquals(1, authService.getVerificationCodes().size());
        // Codes may or may not differ (random), but entry should exist
        assertNotNull(secondCode);
    }

    // ---- register tests ----

    @Test
    void register_validInput_createsUserAndAutoLogins() {
        String email = "newuser@example.com";
        String password = "mypassword";

        // Send verification code first
        authService.sendVerificationCode(email);
        String code = authService.getVerificationCodes().get(email).code();

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        AuthResult result = authService.register(email, code, password);

        assertTrue(result.success());
        assertEquals("注册成功，请登录", result.message());
        assertNull(result.session());

        // Verify user was created
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).createUser(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertEquals(email, createdUser.email());
        assertEquals(AuthServiceImpl.hashPassword(password), createdUser.passwordHash());
        assertEquals("email", createdUser.loginType());
        assertEquals("newuser", createdUser.username());

        // No auto-login after registration
        verify(sessionRepository, never()).saveSession(any(Session.class));
        verify(cacheService, never()).cacheSession(anyString(), any(Session.class), anyLong());

        // Verification code should be consumed
        assertFalse(authService.getVerificationCodes().containsKey(email));
    }

    @Test
    void register_invalidCode_returnsFail() {
        String email = "user@example.com";
        authService.sendVerificationCode(email);

        AuthResult result = authService.register(email, "000000", "password");

        assertFalse(result.success());
        assertEquals("验证码错误，请检查后重试", result.message());
        verify(userRepository, never()).createUser(any());
    }

    @Test
    void register_noCodeSent_returnsFail() {
        AuthResult result = authService.register("user@example.com", "123456", "password");

        assertFalse(result.success());
        assertEquals("验证码无效或未发送，请重新获取验证码", result.message());
        verify(userRepository, never()).createUser(any());
    }

    @Test
    void register_expiredCode_returnsFail() {
        String email = "user@example.com";
        // Manually insert an expired code
        authService.getVerificationCodes().put(email,
                new AuthServiceImpl.VerificationEntry("123456", System.currentTimeMillis() - 1000));

        AuthResult result = authService.register(email, "123456", "password");

        assertFalse(result.success());
        assertEquals("验证码已过期（有效期5分钟），请重新获取", result.message());
        // Expired code should be removed
        assertFalse(authService.getVerificationCodes().containsKey(email));
    }

    @Test
    void register_existingEmail_returnsFail() {
        String email = "existing@example.com";
        authService.sendVerificationCode(email);
        String code = authService.getVerificationCodes().get(email).code();

        User existingUser = new User("u1", email, "hash", "existing", null, "email", false, System.currentTimeMillis());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        AuthResult result = authService.register(email, code, "password");

        assertFalse(result.success());
        assertEquals("该邮箱已注册，请直接登录", result.message());
        verify(userRepository, never()).createUser(any());
    }

    @Test
    void register_nullEmail_returnsFail() {
        AuthResult result = authService.register(null, "123456", "password");
        assertFalse(result.success());
        assertEquals("邮箱不能为空", result.message());
    }

    @Test
    void register_nullCode_returnsFail() {
        AuthResult result = authService.register("user@example.com", null, "password");
        assertFalse(result.success());
        assertEquals("验证码不能为空", result.message());
    }

    @Test
    void register_nullPassword_returnsFail() {
        AuthResult result = authService.register("user@example.com", "123456", null);
        assertFalse(result.success());
        assertEquals("密码不能为空", result.message());
    }

    // ---- logout tests ----

    @Test
    void logout_withExistingSession_clearsBothLocalAndRedis() {
        Session session = new Session("token123", "u1", "user", "user@example.com", "email",
                System.currentTimeMillis() + 100000, 1700000000000L, false);
        when(sessionRepository.loadSession()).thenReturn(Optional.of(session));

        authService.logout();

        verify(cacheService).removeCachedSession("token123");
        verify(sessionRepository).clearSession();
    }

    @Test
    void logout_withNoSession_clearsLocalOnly() {
        when(sessionRepository.loadSession()).thenReturn(Optional.empty());

        authService.logout();

        verify(cacheService, never()).removeCachedSession(anyString());
        verify(sessionRepository).clearSession();
    }

    // ---- getStoredSession tests ----

    @Test
    void getStoredSession_delegatesToRepository() {
        Session session = new Session("token", "u1", "user", "user@example.com", "email",
                System.currentTimeMillis() + 100000, 1700000000000L, false);
        when(sessionRepository.loadSession()).thenReturn(Optional.of(session));

        Optional<Session> result = authService.getStoredSession();

        assertTrue(result.isPresent());
        assertEquals("token", result.get().token());
    }

    @Test
    void getStoredSession_emptyWhenNoSession() {
        when(sessionRepository.loadSession()).thenReturn(Optional.empty());

        Optional<Session> result = authService.getStoredSession();

        assertTrue(result.isEmpty());
    }

    // ---- Feishu OAuth tests ----

    @Test
    void loginWithFeishu_successfulFlow_returnsSuccess() {
        String code = "feishu_auth_code";
        String accessToken = "feishu_access_token";
        FeishuOAuthService.FeishuUserInfo feishuUser =
                new FeishuOAuthService.FeishuUserInfo("fs_user_123", "张三", "zhangsan@example.com");

        when(feishuOAuthService.exchangeCodeForToken(code)).thenReturn(accessToken);
        when(feishuOAuthService.getUserInfo(accessToken)).thenReturn(feishuUser);
        when(userRepository.findByFeishuUserId("fs_user_123")).thenReturn(Optional.empty());

        AuthResult result = authService.loginWithFeishu(code);

        assertTrue(result.success());
        assertEquals("飞书登录成功", result.message());
        assertNotNull(result.session());
        assertEquals("feishu", result.session().loginType());
        assertEquals("张三", result.session().username());
        assertEquals("zhangsan@example.com", result.session().email());

        // Verify user was created
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).createUser(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertEquals("fs_user_123", createdUser.feishuUserId());
        assertEquals("feishu", createdUser.loginType());
        assertNull(createdUser.passwordHash());

        // Verify session was saved locally and cached
        verify(sessionRepository).saveSession(any(Session.class));
        verify(cacheService).cacheSession(anyString(), any(Session.class), eq(AuthServiceImpl.SESSION_TTL_SECONDS));
    }

    @Test
    void loginWithFeishu_existingUser_returnsSuccess() {
        String code = "feishu_auth_code";
        String accessToken = "feishu_access_token";
        FeishuOAuthService.FeishuUserInfo feishuUser =
                new FeishuOAuthService.FeishuUserInfo("fs_user_123", "张三", "zhangsan@example.com");
        User existingUser = new User("u1", "zhangsan@example.com", null, "张三",
                "fs_user_123", "feishu", false, System.currentTimeMillis());

        when(feishuOAuthService.exchangeCodeForToken(code)).thenReturn(accessToken);
        when(feishuOAuthService.getUserInfo(accessToken)).thenReturn(feishuUser);
        when(userRepository.findByFeishuUserId("fs_user_123")).thenReturn(Optional.of(existingUser));

        AuthResult result = authService.loginWithFeishu(code);

        assertTrue(result.success());
        assertEquals("u1", result.session().userId());
        assertEquals("feishu", result.session().loginType());

        // Should NOT create a new user
        verify(userRepository, never()).createUser(any());
    }

    @Test
    void loginWithFeishu_nullCode_returnsFail() {
        AuthResult result = authService.loginWithFeishu(null);
        assertFalse(result.success());
        assertEquals("授权码不能为空", result.message());
    }

    @Test
    void loginWithFeishu_blankCode_returnsFail() {
        AuthResult result = authService.loginWithFeishu("  ");
        assertFalse(result.success());
        assertEquals("授权码不能为空", result.message());
    }

    @Test
    void loginWithFeishu_tokenExchangeFails_returnsFail() {
        when(feishuOAuthService.exchangeCodeForToken("bad_code")).thenReturn(null);

        AuthResult result = authService.loginWithFeishu("bad_code");

        assertFalse(result.success());
        assertEquals("飞书授权失败，请检查应用配置（app_id/app_secret）", result.message());
        assertNull(result.session());
    }

    @Test
    void loginWithFeishu_userInfoFails_returnsFail() {
        when(feishuOAuthService.exchangeCodeForToken("code")).thenReturn("token");
        when(feishuOAuthService.getUserInfo("token")).thenReturn(null);

        AuthResult result = authService.loginWithFeishu("code");

        assertFalse(result.success());
        assertEquals("获取飞书用户信息失败，请检查应用权限配置", result.message());
    }

    @Test
    void loginWithFeishu_emptyUserId_returnsFail() {
        when(feishuOAuthService.exchangeCodeForToken("code")).thenReturn("token");
        when(feishuOAuthService.getUserInfo("token"))
                .thenReturn(new FeishuOAuthService.FeishuUserInfo("", "name", "email@test.com"));

        AuthResult result = authService.loginWithFeishu("code");

        assertFalse(result.success());
        assertEquals("获取飞书用户信息失败，请检查应用权限配置", result.message());
    }

    @Test
    void getFeishuOAuthUrl_delegatesToFeishuOAuthService() {
        when(feishuOAuthService.getAuthorizationUrl()).thenReturn("https://feishu.cn/oauth?app_id=test");

        String url = authService.getFeishuOAuthUrl();

        assertEquals("https://feishu.cn/oauth?app_id=test", url);
        verify(feishuOAuthService).getAuthorizationUrl();
    }

    // ---- hashPassword tests ----

    @Test
    void hashPassword_producesConsistentHash() {
        String hash1 = AuthServiceImpl.hashPassword("test");
        String hash2 = AuthServiceImpl.hashPassword("test");
        assertEquals(hash1, hash2);
    }

    @Test
    void hashPassword_differentPasswordsDifferentHashes() {
        String hash1 = AuthServiceImpl.hashPassword("password1");
        String hash2 = AuthServiceImpl.hashPassword("password2");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashPassword_produces64CharHex() {
        String hash = AuthServiceImpl.hashPassword("test");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }

    // ---- extractUsername tests ----

    @Test
    void extractUsername_extractsBeforeAt() {
        assertEquals("john", AuthServiceImpl.extractUsername("john@example.com"));
    }

    @Test
    void extractUsername_noAtSign_returnsFullEmail() {
        assertEquals("noemail", AuthServiceImpl.extractUsername("noemail"));
    }
}
