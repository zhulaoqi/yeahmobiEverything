package com.yeahmobi.everything.auth;

/**
 * Represents a user account stored in the MySQL database.
 *
 * @param id            the unique user ID (UUID)
 * @param email         the user's email address
 * @param passwordHash  the hashed password (null for feishu login)
 * @param username      the display name
 * @param feishuUserId  the feishu user ID (null for email login)
 * @param loginType     the login type ("email" or "feishu")
 * @param isAdmin       whether the user is an admin
 * @param createdAt     the account creation timestamp (epoch millis)
 */
public record User(
    String id,
    String email,
    String passwordHash,
    String username,
    String feishuUserId,
    String loginType,
    boolean isAdmin,
    long createdAt
) {}
