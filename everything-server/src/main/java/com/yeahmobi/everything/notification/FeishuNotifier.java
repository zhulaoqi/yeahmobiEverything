package com.yeahmobi.everything.notification;

import com.yeahmobi.everything.feedback.Feedback;
import com.yeahmobi.everything.personalskill.PersonalSkill;

/**
 * Interface for sending feedback notifications via Feishu webhook.
 */
public interface FeishuNotifier {

    /**
     * Sends a feedback notification to administrators via Feishu webhook.
     * The notification includes the user's name, submission time, and feedback content
     * formatted as a Feishu interactive card message.
     *
     * @param feedback the feedback to notify about
     * @return true if the notification was sent successfully, false otherwise
     */
    boolean sendFeedbackNotification(Feedback feedback);

    /**
     * Sends a private notification to admins for personal skill review.
     *
     * @param skill the personal skill submitted for review
     * @return true if the notification was sent successfully, false otherwise
     */
    boolean sendPersonalSkillReviewNotification(PersonalSkill skill);
}
