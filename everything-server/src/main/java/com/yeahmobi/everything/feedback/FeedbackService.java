package com.yeahmobi.everything.feedback;

/**
 * Service interface for user feedback operations.
 */
public interface FeedbackService {

    /**
     * Submits user feedback. The feedback is persisted to the database
     * and a notification is sent to administrators via Feishu webhook.
     *
     * @param content  the feedback content text
     * @param userId   the user ID submitting the feedback
     * @param username the name of the user submitting the feedback
     * @return the result of the submission
     */
    FeedbackResult submitFeedback(String content, String userId, String username);
}
