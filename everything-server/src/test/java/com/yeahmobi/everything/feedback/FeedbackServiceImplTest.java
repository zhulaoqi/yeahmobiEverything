package com.yeahmobi.everything.feedback;

import com.yeahmobi.everything.notification.FeishuNotifier;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FeedbackServiceImpl}.
 * Uses Mockito to mock FeedbackRepository and FeishuNotifier.
 */
class FeedbackServiceImplTest {

    private FeedbackRepository feedbackRepository;
    private FeishuNotifier feishuNotifier;
    private FeedbackServiceImpl feedbackService;

    @BeforeEach
    void setUp() {
        feedbackRepository = mock(FeedbackRepository.class);
        feishuNotifier = mock(FeishuNotifier.class);
        feedbackService = new FeedbackServiceImpl(feedbackRepository, feishuNotifier);
    }

    // --- Successful submission ---

    @Test
    void submitFeedbackSuccessfully() {
        // Arrange
        when(feishuNotifier.sendFeedbackNotification(any(Feedback.class))).thenReturn(true);

        // Act
        FeedbackResult result = feedbackService.submitFeedback("需要翻译助手", "u-1", "张三");

        // Assert
        assertTrue(result.success());
        assertEquals("感谢你的反馈，我们已收到并进入评估流程，将持续优化体验。", result.message());
        verify(feedbackRepository).saveFeedback(any(Feedback.class));
        verify(feishuNotifier).sendFeedbackNotification(any(Feedback.class));
    }

    @Test
    void submitFeedbackSavesFeedbackWithCorrectFields() {
        // Arrange
        when(feishuNotifier.sendFeedbackNotification(any(Feedback.class))).thenReturn(true);
        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);

        // Act
        feedbackService.submitFeedback("需要数据分析工具", "u-1", "李四");

        // Assert
        verify(feedbackRepository).saveFeedback(captor.capture());
        Feedback saved = captor.getValue();
        assertNotNull(saved.id());
        assertFalse(saved.id().isBlank());
        assertEquals("李四", saved.username());
        assertEquals("需要数据分析工具", saved.content());
        assertEquals("pending", saved.status());
        assertTrue(saved.timestamp() > 0);
    }

    @Test
    void submitFeedbackSendsNotificationWithSameFeedback() {
        // Arrange
        ArgumentCaptor<Feedback> repoCaptor = ArgumentCaptor.forClass(Feedback.class);
        ArgumentCaptor<Feedback> notifierCaptor = ArgumentCaptor.forClass(Feedback.class);
        when(feishuNotifier.sendFeedbackNotification(any(Feedback.class))).thenReturn(true);

        // Act
        feedbackService.submitFeedback("反馈内容", "u-1", "用户A");

        // Assert
        verify(feedbackRepository).saveFeedback(repoCaptor.capture());
        verify(feishuNotifier).sendFeedbackNotification(notifierCaptor.capture());
        assertEquals(repoCaptor.getValue(), notifierCaptor.getValue());
    }

    // --- Feishu notification failure does not affect submission ---

    @Test
    void submitFeedbackSucceedsEvenWhenFeishuNotificationFails() {
        // Arrange
        when(feishuNotifier.sendFeedbackNotification(any(Feedback.class))).thenReturn(false);

        // Act
        FeedbackResult result = feedbackService.submitFeedback("反馈内容", "u-1", "用户B");

        // Assert
        assertTrue(result.success());
        assertEquals("感谢你的反馈，我们已收到并进入评估流程，将持续优化体验。", result.message());
        verify(feedbackRepository).saveFeedback(any(Feedback.class));
    }

    @Test
    void submitFeedbackSucceedsEvenWhenFeishuNotifierThrowsException() {
        // Arrange
        when(feishuNotifier.sendFeedbackNotification(any(Feedback.class)))
                .thenThrow(new RuntimeException("Webhook connection failed"));

        // Act
        FeedbackResult result = feedbackService.submitFeedback("反馈内容", "u-1", "用户C");

        // Assert
        assertTrue(result.success());
        assertEquals("感谢你的反馈，我们已收到并进入评估流程，将持续优化体验。", result.message());
    }

    // --- Database failure ---

    @Test
    void submitFeedbackFailsWhenDatabaseThrowsException() {
        // Arrange
        doThrow(new RuntimeException("Database connection failed"))
                .when(feedbackRepository).saveFeedback(any(Feedback.class));

        // Act
        FeedbackResult result = feedbackService.submitFeedback("反馈内容", "u-1", "用户D");

        // Assert
        assertFalse(result.success());
        assertTrue(result.message().contains("反馈提交失败"));
    }

    // --- Input validation ---

    @Test
    void submitFeedbackFailsWithNullContent() {
        // Act
        FeedbackResult result = feedbackService.submitFeedback(null, "u-1", "用户");

        // Assert
        assertFalse(result.success());
        assertEquals("反馈内容不能为空", result.message());
        verify(feedbackRepository, never()).saveFeedback(any());
        verify(feishuNotifier, never()).sendFeedbackNotification(any());
    }

    @Test
    void submitFeedbackFailsWithBlankContent() {
        // Act
        FeedbackResult result = feedbackService.submitFeedback("   ", "u-1", "用户");

        // Assert
        assertFalse(result.success());
        assertEquals("反馈内容不能为空", result.message());
        verify(feedbackRepository, never()).saveFeedback(any());
    }

    @Test
    void submitFeedbackFailsWithNullUsername() {
        // Act
        FeedbackResult result = feedbackService.submitFeedback("反馈内容", "u-1", null);

        // Assert
        assertFalse(result.success());
        assertEquals("用户名不能为空", result.message());
        verify(feedbackRepository, never()).saveFeedback(any());
    }

    @Test
    void submitFeedbackFailsWithBlankUsername() {
        // Act
        FeedbackResult result = feedbackService.submitFeedback("反馈内容", "u-1", "");

        // Assert
        assertFalse(result.success());
        assertEquals("用户名不能为空", result.message());
        verify(feedbackRepository, never()).saveFeedback(any());
    }
}
