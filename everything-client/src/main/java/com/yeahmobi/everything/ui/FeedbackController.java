package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.feedback.FeedbackResult;
import com.yeahmobi.everything.feedback.FeedbackService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the feedback page.
 * <p>
 * Provides a text area for users to enter feedback content and a submit button.
 * Displays success or failure messages after submission.
 * </p>
 */
public class FeedbackController {

    private static final Logger LOGGER = Logger.getLogger(FeedbackController.class.getName());

    @FXML private TextArea feedbackInput;
    @FXML private Label resultLabel;
    @FXML private Button submitButton;

    private FeedbackService feedbackService;
    private String username;
    private String userId;

    public void setFeedbackService(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @FXML
    public void onSubmitFeedback() {
        String content = feedbackInput.getText();
        if (content == null || content.isBlank()) {
            showResult(false, "请输入反馈内容");
            return;
        }

        if (feedbackService == null) {
            showResult(false, "反馈服务不可用");
            return;
        }

        submitButton.setDisable(true);
        try {
            FeedbackResult result = feedbackService.submitFeedback(content.trim(), userId, username);
            showResult(result.success(), result.message());
            if (result.success()) {
                feedbackInput.clear();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to submit feedback", e);
            showResult(false, "反馈提交失败，请稍后重试");
        } finally {
            submitButton.setDisable(false);
        }
    }

    private void showResult(boolean success, String message) {
        resultLabel.setText(message);
        resultLabel.setManaged(true);
        resultLabel.setVisible(true);
        if (success) {
            resultLabel.getStyleClass().removeAll("login-error-label");
            resultLabel.getStyleClass().add("section-subtitle");
            resultLabel.setStyle("-fx-text-fill: #1F883D;");
        } else {
            resultLabel.getStyleClass().removeAll("section-subtitle");
            resultLabel.getStyleClass().add("login-error-label");
            resultLabel.setStyle("");
        }
    }

    // ---- Package-private accessors for testing ----

    TextArea getFeedbackInput() { return feedbackInput; }
    Label getResultLabel() { return resultLabel; }
    Button getSubmitButton() { return submitButton; }
}
