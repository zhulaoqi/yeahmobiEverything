package com.yeahmobi.everything.auth;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.SessionRepository;
import com.yeahmobi.everything.repository.mysql.UserRepository;
import net.jqwik.api.*;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for invalid verification code rejection during registration.
 *
 * <p>Feature: yeahmobi-everything, Property 32: 无效验证码拒绝注册</p>
 */
class AuthServiceInvalidCodePropertyTest {

    /**
     * Creates an AuthServiceImpl with mocked dependencies.
     * The UserRepository mock verifies that createUser is never called.
     */
    private record TestContext(AuthServiceImpl authService, UserRepository userRepository) {}

    private TestContext createTestContext() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        CacheService cacheService = mock(CacheService.class);
        FeishuOAuthService feishuOAuthService = mock(FeishuOAuthService.class);
        UserRepository userRepository = mock(UserRepository.class);

        // No existing users
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        AuthServiceImpl authService = new AuthServiceImpl(
                sessionRepository, userRepository, cacheService, feishuOAuthService);

        return new TestContext(authService, userRepository);
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 32: 无效验证码拒绝注册
    void registerWithWrongCode_shouldFailAndNotCreateUser(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password,
            @ForAll("wrongCodes") String wrongCode) {
        // **Validates: Requirements 1.9**

        TestContext ctx = createTestContext();
        AuthServiceImpl authService = ctx.authService();
        UserRepository userRepository = ctx.userRepository();

        // Step 1: Send verification code to get a valid entry
        boolean codeSent = authService.sendVerificationCode(email);
        assertTrue(codeSent, "Verification code should be sent successfully");

        // Step 2: Retrieve the real verification code
        ConcurrentHashMap<String, AuthServiceImpl.VerificationEntry> codes =
                authService.getVerificationCodes();
        AuthServiceImpl.VerificationEntry entry = codes.get(email);
        assertNotNull(entry, "Verification entry should exist for the email");
        String realCode = entry.code();

        // Ensure the wrong code is actually different from the real code
        Assume.that(!wrongCode.equals(realCode));

        // Step 3: Attempt registration with the wrong code
        AuthResult result = authService.register(email, wrongCode, password);

        // Assert: registration should fail
        assertFalse(result.success(),
                "Registration with wrong verification code should return success=false");
        assertNull(result.session(),
                "Registration with wrong verification code should return null session");

        // Assert: no user account should be created
        verify(userRepository, never()).createUser(any(User.class));
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 32: 无效验证码拒绝注册
    void registerWithExpiredCode_shouldFailAndNotCreateUser(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password) {
        // **Validates: Requirements 1.9**

        TestContext ctx = createTestContext();
        AuthServiceImpl authService = ctx.authService();
        UserRepository userRepository = ctx.userRepository();

        // Step 1: Send verification code
        boolean codeSent = authService.sendVerificationCode(email);
        assertTrue(codeSent, "Verification code should be sent successfully");

        // Step 2: Retrieve the real verification code
        ConcurrentHashMap<String, AuthServiceImpl.VerificationEntry> codes =
                authService.getVerificationCodes();
        AuthServiceImpl.VerificationEntry entry = codes.get(email);
        assertNotNull(entry, "Verification entry should exist for the email");
        String realCode = entry.code();

        // Step 3: Simulate expiration by replacing the entry with an already-expired one
        codes.put(email, new AuthServiceImpl.VerificationEntry(realCode, System.currentTimeMillis() - 1));

        // Step 4: Attempt registration with the correct code but expired entry
        AuthResult result = authService.register(email, realCode, password);

        // Assert: registration should fail due to expired code
        assertFalse(result.success(),
                "Registration with expired verification code should return success=false");
        assertNull(result.session(),
                "Registration with expired verification code should return null session");

        // Assert: no user account should be created
        verify(userRepository, never()).createUser(any(User.class));
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

    @Provide
    Arbitrary<String> wrongCodes() {
        // Generate codes that are likely different from the real 6-digit code
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(10)
                .numeric();
    }
}
