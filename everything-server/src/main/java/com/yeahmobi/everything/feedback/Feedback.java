package com.yeahmobi.everything.feedback;

/**
 * Represents a user feedback submission.
 *
 * @param id        unique feedback identifier (UUID)
 * @param userId    the user ID who submitted the feedback
 * @param username  the name of the user who submitted the feedback
 * @param content   the feedback content text
 * @param timestamp the submission time as epoch milliseconds
 * @param status    the feedback status: "pending" or "processed"
 */
public record Feedback(String id, String userId, String username, String content, long timestamp, String status) {
}
