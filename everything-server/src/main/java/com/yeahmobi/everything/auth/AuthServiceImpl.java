package com.yeahmobi.everything.auth;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.SessionRepository;
import com.yeahmobi.everything.repository.mysql.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link AuthService} providing email/password login,
 * registration with verification codes, session management, and logout.
 * <p>
 * Feishu OAuth methods are left as stubs for task 2.3.
 * </p>
 *
 * <h3>Password hashing:</h3>
 * Uses SHA-256 via {@link MessageDigest} for simplicity.
 *
 * <h3>Verification code storage:</h3>
 * Uses a {@link ConcurrentHashMap} with expiry timestamps.
 * Codes expire after 5 minutes.
 */
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** Session TTL in seconds: 7 days */
    static final long SESSION_TTL_SECONDS = 7 * 24 * 60 * 60;

    /** Session TTL in milliseconds: 7 days */
    static final long SESSION_TTL_MILLIS = SESSION_TTL_SECONDS * 1000;

    /** Verification code expiry in milliseconds: 5 minutes */
    static final long VERIFICATION_CODE_EXPIRY_MILLIS = 5 * 60 * 1000;

    /** Verification code length */
    static final int VERIFICATION_CODE_LENGTH = 6;

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final CacheService cacheService;
    private final FeishuOAuthService feishuOAuthService;
    private final EmailService emailService;

    /** In-memory storage for verification codes: email -> VerificationEntry */
    private final ConcurrentHashMap<String, VerificationEntry> verificationCodes = new ConcurrentHashMap<>();

    /**
     * Creates an AuthServiceImpl with all dependencies including email service.
     *
     * @param sessionRepository  the local session repository
     * @param userRepository     the MySQL user repository
     * @param cacheService       the Redis cache service
     * @param feishuOAuthService the Feishu OAuth service
     * @param emailService       the email sending service
     */
    public AuthServiceImpl(SessionRepository sessionRepository,
                           UserRepository userRepository,
                           CacheService cacheService,
                           FeishuOAuthService feishuOAuthService,
                           EmailService emailService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.cacheService = cacheService;
        this.feishuOAuthService = feishuOAuthService;
        this.emailService = emailService;
    }

    /**
     * Creates an AuthServiceImpl without email service (backward compatible).
     * Verification codes will only be logged to console.
     */
    public AuthServiceImpl(SessionRepository sessionRepository,
                           UserRepository userRepository,
                           CacheService cacheService,
                           FeishuOAuthService feishuOAuthService) {
        this(sessionRepository, userRepository, cacheService, feishuOAuthService, null);
    }

    @Override
    public AuthResult loginWithEmail(String email, String password) {
        if (email == null || email.isBlank()) {
            return new AuthResult(false, "邮箱不能为空", null);
        }
        if (password == null || password.isBlank()) {
            return new AuthResult(false, "密码不能为空", null);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return new AuthResult(false, "邮箱或密码错误", null);
        }

        User user = userOpt.get();
        String passwordHash = hashPassword(password);

        if (!passwordHash.equals(user.passwordHash())) {
            return new AuthResult(false, "邮箱或密码错误", null);
        }

        Session session = createSession(user);
        saveSession(session);

        return new AuthResult(true, "登录成功", session);
    }

    @Override
    public AuthResult loginWithFeishu(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            return new AuthResult(false, "授权码不能为空", null);
        }

        // Check if MySQL is available (required for Feishu login)
        if (userRepository == null) {
            log.warn("Feishu login requires MySQL connection, but MySQL is unavailable");
            return new AuthResult(false, "飞书登录需要 MySQL 数据库支持，请检查数据库连接配置", null);
        }

        // Step 1: Exchange code for access token
        log.info("Attempting to exchange Feishu authorization code for access token");
        String accessToken = feishuOAuthService.exchangeCodeForToken(authorizationCode);
        if (accessToken == null) {
            log.warn("Failed to exchange Feishu authorization code for token");
            return new AuthResult(false, "飞书授权失败，请检查应用配置（app_id/app_secret）", null);
        }

        // Step 2: Get user info from Feishu
        log.info("Attempting to fetch Feishu user info with access token");
        FeishuOAuthService.FeishuUserInfo feishuUser = feishuOAuthService.getUserInfo(accessToken);
        if (feishuUser == null || feishuUser.userId() == null || feishuUser.userId().isBlank()) {
            log.warn("Failed to get Feishu user info or user_id is empty");
            return new AuthResult(false, "获取飞书用户信息失败，请检查应用权限配置", null);
        }
        
        log.info("Successfully retrieved Feishu user info: userId={}, name={}, email={}",
                feishuUser.userId(), feishuUser.name(), feishuUser.email());

        // Step 3: Find or create user by feishu_user_id
        User user;
        Optional<User> existingUser = userRepository.findByFeishuUserId(feishuUser.userId());
        if (existingUser.isPresent()) {
            user = existingUser.get();
        } else {
            // Create new user for Feishu login
            String userId = UUID.randomUUID().toString();
            String username = feishuUser.name() != null && !feishuUser.name().isBlank()
                    ? feishuUser.name() : "feishu_user";
            String email = feishuUser.email();
            long createdAt = System.currentTimeMillis();

            user = new User(userId, email, null, username, feishuUser.userId(), "feishu", false, createdAt);
            userRepository.createUser(user);
        }

        // Step 4: Create session with loginType="feishu"
        Session session = createFeishuSession(user);

        // Step 5: Save session to local and Redis
        saveSession(session);

        return new AuthResult(true, "飞书登录成功", session);
    }

    @Override
    public String getFeishuOAuthUrl() {
        return feishuOAuthService.getAuthorizationUrl();
    }

    @Override
    public boolean sendVerificationCode(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String code = generateVerificationCode();
        long expiresAt = System.currentTimeMillis() + VERIFICATION_CODE_EXPIRY_MILLIS;
        verificationCodes.put(email, new VerificationEntry(code, expiresAt));

        // Send via EmailService if available, otherwise fall back to console log
        if (emailService != null) {
            boolean sent = emailService.sendVerificationCode(email, code);
            if (!sent) {
                log.warn("Email sending failed for {}, code: {}", email, code);
            }
            return sent;
        } else {
            log.info("【EmailService 未配置，控制台输出】验证码: {} -> {}", email, code);
            return true;
        }
    }

    @Override
    public AuthResult register(String email, String verificationCode, String password) {
        if (email == null || email.isBlank()) {
            return new AuthResult(false, "邮箱不能为空", null);
        }
        if (verificationCode == null || verificationCode.isBlank()) {
            return new AuthResult(false, "验证码不能为空", null);
        }
        if (password == null || password.isBlank()) {
            return new AuthResult(false, "密码不能为空", null);
        }

        // Verify the verification code
        VerificationEntry entry = verificationCodes.get(email);
        if (entry == null) {
            return new AuthResult(false, "验证码无效或未发送，请重新获取验证码", null);
        }
        if (System.currentTimeMillis() > entry.expiresAt()) {
            verificationCodes.remove(email);
            return new AuthResult(false, "验证码已过期（有效期5分钟），请重新获取", null);
        }
        if (!entry.code().equals(verificationCode)) {
            // Don't remove code on wrong attempt - allow retry
            return new AuthResult(false, "验证码错误，请检查后重试", null);
        }

        // Check if user already exists BEFORE removing verification code
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            // Don't remove code if user exists - they might want to try a different email
            return new AuthResult(false, "该邮箱已注册，请直接登录", null);
        }

        // Only remove the verification code after successful validation and before creating user
        verificationCodes.remove(email);

        // Create user account
        String userId = UUID.randomUUID().toString();
        String passwordHash = hashPassword(password);
        String username = extractUsername(email);
        long createdAt = System.currentTimeMillis();

        User user = new User(userId, email, passwordHash, username, null, "email", false, createdAt);
        userRepository.createUser(user);

        // Do not auto-login after registration; user should login explicitly
        return new AuthResult(true, "注册成功，请登录", null);
    }

    @Override
    public Optional<Session> getStoredSession() {
        return sessionRepository.loadSession();
    }

    @Override
    public void logout() {
        Optional<Session> sessionOpt = sessionRepository.loadSession();
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            // Clear Redis cache
            cacheService.removeCachedSession(session.token());
        }
        // Clear local session
        sessionRepository.clearSession();
    }

    // ---- Internal helpers ----

    /**
     * Creates a new session for the given user.
     */
    Session createSession(User user) {
        String token = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + SESSION_TTL_MILLIS;
        return new Session(
                token,
                user.id(),
                user.username(),
                user.email(),
                "email",
                expiresAt,
                user.createdAt(),
                user.isAdmin()
        );
    }

    /**
     * Creates a new session for a Feishu-authenticated user.
     */
    Session createFeishuSession(User user) {
        String token = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + SESSION_TTL_MILLIS;
        return new Session(
                token,
                user.id(),
                user.username(),
                user.email(),
                "feishu",
                expiresAt,
                user.createdAt(),
                user.isAdmin()
        );
    }

    /**
     * Saves a session to both local storage and Redis cache.
     */
    void saveSession(Session session) {
        sessionRepository.saveSession(session);
        cacheService.cacheSession(session.token(), session, SESSION_TTL_SECONDS);
    }

    /**
     * Hashes a password using SHA-256.
     *
     * @param password the plaintext password
     * @return the hex-encoded SHA-256 hash
     */
    static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts a byte array to a lowercase hex string.
     */
    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Generates a random 6-digit verification code.
     *
     * @return a 6-digit string code
     */
    String generateVerificationCode() {
        int code = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(code);
    }

    /**
     * Extracts a username from an email address (the part before @).
     *
     * @param email the email address
     * @return the extracted username
     */
    static String extractUsername(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }

    /**
     * Returns the verification codes map. Exposed for testing purposes.
     */
    ConcurrentHashMap<String, VerificationEntry> getVerificationCodes() {
        return verificationCodes;
    }

    /**
     * Represents a verification code entry with expiry.
     *
     * @param code      the 6-digit verification code
     * @param expiresAt the expiration timestamp (epoch millis)
     */
    record VerificationEntry(String code, long expiresAt) {}
}
