package com.yeahmobi.everything.auth;

/**
 * Represents the result of an authentication operation.
 *
 * @param success whether the authentication was successful
 * @param message a message describing the result
 * @param session the session if authentication was successful, null otherwise
 */
public record AuthResult(boolean success, String message, Session session) {}
