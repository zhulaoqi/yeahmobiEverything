package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.auth.User;

import java.util.Optional;

/**
 * Repository interface for managing user accounts in the MySQL database.
 * <p>
 * Provides CRUD operations for user records. Users can be looked up
 * by email (for email/password login), by feishu user ID (for feishu
 * OAuth login), or by their unique user ID.
 * </p>
 */
public interface UserRepository {

    /**
     * Creates a new user account in the database.
     *
     * @param user the user to create
     */
    void createUser(User user);

    /**
     * Finds a user by their email address.
     *
     * @param email the email address to search for
     * @return an Optional containing the user if found, or empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by their feishu user ID.
     *
     * @param feishuUserId the feishu user ID to search for
     * @return an Optional containing the user if found, or empty otherwise
     */
    Optional<User> findByFeishuUserId(String feishuUserId);

    /**
     * Finds a user by their unique user ID.
     *
     * @param userId the user ID to search for
     * @return an Optional containing the user if found, or empty otherwise
     */
    Optional<User> findById(String userId);

    /**
     * Promotes a user to admin by user ID.
     *
     * @param userId the user ID
     */
    void promoteToAdminById(String userId);

    /**
     * Promotes a user to admin by email.
     *
     * @param email the user email
     */
    void promoteToAdminByEmail(String email);
}
