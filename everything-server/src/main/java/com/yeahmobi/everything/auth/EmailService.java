package com.yeahmobi.everything.auth;

/**
 * Service interface for sending emails.
 * <p>
 * Implementations may use SMTP, third-party APIs, or simulate sending
 * for testing purposes.
 * </p>
 */
public interface EmailService {

    /**
     * Sends a verification code email to the specified address.
     *
     * @param toEmail the recipient email address
     * @param code    the 6-digit verification code
     * @return true if the email was sent successfully, false otherwise
     */
    boolean sendVerificationCode(String toEmail, String code);
}
