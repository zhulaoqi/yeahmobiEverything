package com.yeahmobi.everything.feedback;

import com.yeahmobi.everything.notification.FeishuNotifier;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Implementation of {@link FeedbackService}.
 * Persists feedback to MySQL and sends a Feishu notification to administrators.
 * <p>
 * Per the design document error handling specification:
 * "Webhook 调用失败不影响反馈提交的成功状态" —
 * Feishu notification failure does not affect the feedback submission result.
 * </p>
 */
public class FeedbackServiceImpl implements FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackServiceImpl.class);

    private final FeedbackRepository feedbackRepository;
    private final FeishuNotifier feishuNotifier;

    public FeedbackServiceImpl(FeedbackRepository feedbackRepository, FeishuNotifier feishuNotifier) {
        this.feedbackRepository = feedbackRepository;
        this.feishuNotifier = feishuNotifier;
    }

    @Override
    public FeedbackResult submitFeedback(String content, String userId, String username) {
        if (content == null || content.isBlank()) {
            return new FeedbackResult(false, "反馈内容不能为空");
        }
        if (userId == null || userId.isBlank()) {
            return new FeedbackResult(false, "用户信息缺失，请重新登录");
        }
        if (username == null || username.isBlank()) {
            return new FeedbackResult(false, "用户名不能为空");
        }

        try {
            String id = UUID.randomUUID().toString();
            long timestamp = System.currentTimeMillis();
            Feedback feedback = new Feedback(id, userId, username, content, timestamp, "pending");

            feedbackRepository.saveFeedback(feedback);

            // Send Feishu notification asynchronously — failure does not affect submission result
            try {
                feishuNotifier.sendFeedbackNotification(feedback);
            } catch (Exception e) {
                log.error("Failed to send Feishu notification", e);
            }

            return new FeedbackResult(true, "感谢你的反馈，我们已收到并进入评估流程，将持续优化体验。");
        } catch (Exception e) {
            return new FeedbackResult(false, "反馈提交失败: " + e.getMessage());
        }
    }
}
