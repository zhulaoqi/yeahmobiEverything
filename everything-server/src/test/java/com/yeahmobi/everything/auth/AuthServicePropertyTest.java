package com.yeahmobi.everything.auth;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.SessionRepository;
import com.yeahmobi.everything.repository.mysql.UserRepository;
import net.jqwik.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for AuthService email/password login.
 *
 * <p>Feature: yeahmobi-everything, Property 1: 邮箱密码登录结果与凭证有效性一致</p>
 */
class AuthServicePropertyTest {

    /**
     * Creates a fresh AuthServiceImpl with mocked dependencies.
     * The UserRepository is configured based on the test scenario.
     */
    private AuthServiceImpl createAuthService(UserRepository userRepository) {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        CacheService cacheService = mock(CacheService.class);
        FeishuOAuthService feishuOAuthService = mock(FeishuOAuthService.class);
        return new AuthServiceImpl(sessionRepository, userRepository, cacheService, feishuOAuthService);
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 1: 邮箱密码登录结果与凭证有效性一致
    void loginWithValidCredentials_returnsSuccessWithEmailSession(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password) {
        // **Validates: Requirements 1.4, 1.5**

        // Arrange: create a user with the hashed password in the repository
        String passwordHash = AuthServiceImpl.hashPassword(password);
        String userId = "user-" + email.hashCode();
        String username = email.substring(0, email.indexOf('@'));
        User user = new User(userId, email, passwordHash, username, null, "email", false, System.currentTimeMillis());

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthServiceImpl authService = createAuthService(userRepository);

        // Act
        AuthResult result = authService.loginWithEmail(email, password);

        // Assert: valid credentials -> success=true, non-null Session with loginType="email"
        assertTrue(result.success(), "Valid credentials should return success=true");
        assertNotNull(result.session(), "Valid credentials should return a non-null Session");
        assertEquals("email", result.session().loginType(),
                "Session loginType should be 'email' for email/password login");
        assertNotNull(result.session().token(), "Session token should not be null");
        assertFalse(result.session().token().isBlank(), "Session token should not be blank");
        assertEquals(userId, result.session().userId(), "Session userId should match the user");
        assertEquals(email, result.session().email(), "Session email should match the login email");
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 1: 邮箱密码登录结果与凭证有效性一致
    void loginWithWrongPassword_returnsFailureWithNullSession(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String correctPassword,
            @ForAll("validPasswords") String wrongPassword) {
        // **Validates: Requirements 1.4, 1.5**

        // Skip when the two passwords happen to be the same
        Assume.that(!correctPassword.equals(wrongPassword));

        // Arrange: user exists with correctPassword hash, but we login with wrongPassword
        String correctHash = AuthServiceImpl.hashPassword(correctPassword);
        User user = new User("u1", email, correctHash, "user", null, "email", false, System.currentTimeMillis());

        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthServiceImpl authService = createAuthService(userRepository);

        // Act
        AuthResult result = authService.loginWithEmail(email, wrongPassword);

        // Assert: invalid credentials -> success=false, null Session
        assertFalse(result.success(), "Wrong password should return success=false");
        assertNull(result.session(), "Wrong password should return null Session");
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 1: 邮箱密码登录结果与凭证有效性一致
    void loginWithNonExistentEmail_returnsFailureWithNullSession(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password) {
        // **Validates: Requirements 1.4, 1.5**

        // Arrange: no user found for this email
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        AuthServiceImpl authService = createAuthService(userRepository);

        // Act
        AuthResult result = authService.loginWithEmail(email, password);

        // Assert: non-existent user -> success=false, null Session
        assertFalse(result.success(), "Non-existent email should return success=false");
        assertNull(result.session(), "Non-existent email should return null Session");
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> validEmails() {
        Arbitrary<String> localParts = Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(20)
                .alpha()
                .numeric();
        Arbitrary<String> domains = Arbitraries.of(
                "example.com", "test.org", "corp.io", "yeahmobi.com", "mail.cn");

        return Combinators.combine(localParts, domains)
                .as((local, domain) -> local + "@" + domain);
    }

    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(50)
                .alpha()
                .numeric();
    }
}
