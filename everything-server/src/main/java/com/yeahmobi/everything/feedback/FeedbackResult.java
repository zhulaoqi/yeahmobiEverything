package com.yeahmobi.everything.feedback;

/**
 * Represents the result of a feedback submission operation.
 *
 * @param success whether the feedback was submitted successfully
 * @param message a human-readable message describing the result
 */
public record FeedbackResult(boolean success, String message) {
}
