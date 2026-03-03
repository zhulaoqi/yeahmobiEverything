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
 * Property-based test for register-then-login round-trip.
 *
 * <p>Feature: yeahmobi-everything, Property 31: 注册后登录 round-trip</p>
 */
class AuthServiceRegisterLoginPropertyTest {

    /**
     * Creates an AuthServiceImpl with mocked dependencies.
     * The UserRepository mock tracks created users so that findByEmail
     * returns the user after createUser has been called.
     */
    private record TestContext(AuthServiceImpl authService, UserRepository userRepository) {}

    private TestContext createTestContext() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        CacheService cacheService = mock(CacheService.class);
        FeishuOAuthService feishuOAuthService = mock(FeishuOAuthService.class);

        // Use a real ConcurrentHashMap to track created users
        ConcurrentHashMap<String, User> userStore = new ConcurrentHashMap<>();
        UserRepository userRepository = mock(UserRepository.class);

        // When createUser is called, store the user
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            userStore.put(user.email(), user);
            return null;
        }).when(userRepository).createUser(any(User.class));

        // When findByEmail is called, look up from the store
        when(userRepository.findByEmail(anyString())).thenAnswer(invocation -> {
            String email = invocation.getArgument(0);
            return Optional.ofNullable(userStore.get(email));
        });

        AuthServiceImpl authService = new AuthServiceImpl(
                sessionRepository, userRepository, cacheService, feishuOAuthService);

        return new TestContext(authService, userRepository);
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 31: 注册后登录 round-trip
    void registerThenLogin_shouldSucceed(
            @ForAll("validEmails") String email,
            @ForAll("validPasswords") String password) {
        // **Validates: Requirements 1.8**

        TestContext ctx = createTestContext();
        AuthServiceImpl authService = ctx.authService();

        // Step 1: Send verification code
        boolean codeSent = authService.sendVerificationCode(email);
        assertTrue(codeSent, "Verification code should be sent successfully");

        // Step 2: Retrieve the verification code from the internal map
        ConcurrentHashMap<String, AuthServiceImpl.VerificationEntry> codes =
                authService.getVerificationCodes();
        AuthServiceImpl.VerificationEntry entry = codes.get(email);
        assertNotNull(entry, "Verification entry should exist for the email");
        String verificationCode = entry.code();

        // Step 3: Register with the valid email, code, and password
        AuthResult registerResult = authService.register(email, verificationCode, password);
        assertTrue(registerResult.success(), "Registration should succeed with valid inputs");
        assertNull(registerResult.session(), "Registration does not auto-login; session should be null");

        // Step 4: Login with the same email and password
        AuthResult loginResult = authService.loginWithEmail(email, password);
        assertTrue(loginResult.success(),
                "Login with the same email and password after registration should succeed");
        assertNotNull(loginResult.session(), "Login should return a non-null session");
        assertEquals("email", loginResult.session().loginType(),
                "Session loginType should be 'email'");
        assertEquals(email, loginResult.session().email(),
                "Session email should match the registered email");
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
