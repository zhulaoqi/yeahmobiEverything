package com.yeahmobi.everything.auth;

import java.util.Optional;

/**
 * Service interface for user authentication operations.
 * <p>
 * Supports two login modes: email/password and Feishu OAuth.
 * Also handles user registration with email verification codes,
 * session management, and logout.
 * </p>
 */
public interface AuthService {

    /**
     * Authenticates a user with email and password.
     * <p>
     * Verifies the password against the stored hash. On success, creates a
     * session and saves it to both local storage and Redis cache.
     * </p>
     *
     * @param email    the user's email address
     * @param password the user's password (plaintext, will be hashed for comparison)
     * @return an AuthResult indicating success or failure
     */
    AuthResult loginWithEmail(String email, String password);

    /**
     * Authenticates a user via Feishu OAuth using an authorization code.
     * <p>
     * Exchanges the authorization code for an access token, retrieves user info,
     * and creates a session.
     * </p>
     *
     * @param authorizationCode the Feishu OAuth authorization code
     * @return an AuthResult indicating success or failure
     */
    AuthResult loginWithFeishu(String authorizationCode);

    /**
     * Returns the Feishu OAuth authorization URL for initiating the login flow.
     *
     * @return the Feishu OAuth URL
     */
    String getFeishuOAuthUrl();

    /**
     * Sends a verification code to the specified email address for registration.
     * <p>
     * Generates a 6-digit code, stores it in memory with a 5-minute expiry,
     * and simulates sending it via email (logs the code).
     * </p>
     *
     * @param email the email address to send the verification code to
     * @return true if the code was sent successfully, false otherwise
     */
    boolean sendVerificationCode(String email);

    /**
     * Registers a new user account with email, verification code, and password.
     * <p>
     * Verifies the code, creates the user account with a hashed password,
     * and automatically logs in the new user.
     * </p>
     *
     * @param email            the user's email address
     * @param verificationCode the verification code received via email
     * @param password         the desired password
     * @return an AuthResult indicating success or failure
     */
    AuthResult register(String email, String verificationCode, String password);

    /**
     * Retrieves the stored session from local storage.
     *
     * @return an Optional containing the session if one exists, or empty otherwise
     */
    Optional<Session> getStoredSession();

    /**
     * Logs out the current user by clearing the local session and Redis cache.
     */
    void logout();
}
