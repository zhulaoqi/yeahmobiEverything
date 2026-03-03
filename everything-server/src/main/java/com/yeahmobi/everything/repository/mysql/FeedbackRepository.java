package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.feedback.Feedback;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for feedback persistence in MySQL.
 */
public interface FeedbackRepository {

    /**
     * Saves a new feedback record to the database.
     *
     * @param feedback the feedback to save
     */
    void saveFeedback(Feedback feedback);

    /**
     * Retrieves all feedback records, ordered by timestamp descending.
     *
     * @return list of all feedbacks
     */
    List<Feedback> getAllFeedbacks();

    /**
     * Retrieves a single feedback by its ID.
     *
     * @param feedbackId the feedback ID
     * @return an Optional containing the feedback if found
     */
    Optional<Feedback> getFeedback(String feedbackId);

    /**
     * Updates the status of a feedback record and sets the processed timestamp.
     *
     * @param feedbackId  the feedback ID
     * @param status      the new status ("pending" or "processed")
     * @param processedAt the processing timestamp as epoch milliseconds
     */
    void updateFeedbackStatus(String feedbackId, String status, long processedAt);
}
