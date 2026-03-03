package com.yeahmobi.everything.auth;

/**
 * Represents a user session.
 *
 * @param token     the session token
 * @param userId    the user ID
 * @param username  the username
 * @param email     the user's email
 * @param loginType the login type ("email" or "feishu")
 * @param expiresAt the expiration timestamp (epoch millis)
 * @param createdAt the account creation timestamp (epoch millis)
 * @param isAdmin   whether the user is an admin
 */
public record Session(
    String token,
    String userId,
    String username,
    String email,
    String loginType,
    long expiresAt,
    long createdAt,
    boolean isAdmin
) {}
