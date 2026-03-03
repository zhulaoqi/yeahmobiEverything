package com.yeahmobi.everything.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.chat.ChatMessage;
import com.yeahmobi.everything.chat.ChatResponse;
import com.yeahmobi.everything.chat.ChatService;
import com.yeahmobi.everything.cli.LocalCliGatewayClient;
import com.yeahmobi.everything.skill.Skill;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillType;
import com.yeahmobi.everything.workfollowup.WorkFollowupService;
import com.yeahmobi.everything.workfollowup.WorkTodo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.OverrunStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller for the chat conversation interface.
 * <p>
 * Displays a bubble-style chat layout with user messages on the right (blue)
 * and AI responses on the left (gray). Supports Markdown rendering for AI
 * replies via flexmark-java and WebView, a loading indicator while the LLM
 * processes, and a usage guide overlay for the current Skill.
 * </p>
 */
public class ChatController {

    private static final Logger LOGGER = Logger.getLogger(ChatController.class.getName());
    private static final Pattern CITATION_URL_PATTERN = Pattern.compile(
            "(?i)\\bhttps?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Pattern NUMBERED_TITLE_PATTERN = Pattern.compile("^\\s*\\d+\\.\\s+(.+?)\\s*$");
    private static final DateTimeFormatter TODO_DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private Label skillNameLabel;
    @FXML private Label skillTypeBadge;
    @FXML private Button usageGuideBtn;
    @FXML private Button howToBtn;
    @FXML private Button hideHowToBtn;
    @FXML private HBox chatHeaderBar;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox messagesContainer;
    @FXML private VBox skillHowToPanel;
    @FXML private Label howToScenariosLabel;
    @FXML private Label howToChecklistLabel;
    @FXML private HBox howToExamplesRow;
    @FXML private Label howToOutputFormatLabel;
    @FXML private VBox stepPanel;
    @FXML private HBox chatToolbar;
    @FXML private ScrollPane attachmentStrip;
    @FXML private HBox attachmentRow;
    @FXML private VBox attachmentBatchSummaryBox;
    @FXML private Label attachmentBatchSummaryLabel;
    @FXML private Button attachmentSummaryToggleBtn;
    @FXML private HBox attachmentSummaryActionRow;
    @FXML private Button attachmentAnomalyFilterBtn;
    @FXML private Button attachmentCopyAnomalyBtn;
    @FXML private Button attachmentExportSummaryBtn;
    @FXML private Label attachmentBatchDetailLabel;
    @FXML private Button attachFileBtn;
    @FXML private ChoiceBox<String> quickReminderDayChoice;
    @FXML private ChoiceBox<String> quickReminderTimeChoice;
    @FXML private ChoiceBox<Integer> quickReminderLeadChoice;
    @FXML private Button quickReminderCreateBtn;
    @FXML private VBox planStepBox;
    @FXML private VBox executionStepBox;
    @FXML private VBox reviewStepBox;
    @FXML private Label planContent;
    @FXML private Label executionContent;
    @FXML private Label reviewContent;
    @FXML private HBox loadingBox;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label loadingLabel;
    @FXML private Button stopBtn;
    @FXML private Label errorLabel;
    @FXML private TextArea messageInput;
    @FXML private Button sendBtn;
    @FXML private Button clearHistoryBtn;
    @FXML private Button exportBtn;
    @FXML private Region inputDivider;
    @FXML private VBox inputArea;
    @FXML private HBox marketplaceActionBar;
    @FXML private Button useSkillBtn;

    private ChatService chatService;
    private Skill currentSkill;
    private SkillI18nZhCn currentSkillZh;
    private String currentUserId;
    private String sessionId;
    private Runnable onBackCallback;
    private Runnable onHistoryClearedCallback;
    private Runnable onUseSkillCallback;
    private final List<ChatMessage> conversationHistory = new ArrayList<>();
    private boolean sending = false;
    private boolean inputLocked = false;
    private boolean marketplacePreviewMode = false;
    private HBox streamingRow;
    private Label streamingLabel;
    private StringBuilder streamingBuffer;
    private String lastAiContent;
    private CompletableFuture<ChatResponse> activeStreamingFuture;
    private boolean stopRequested = false;
    private final List<File> pendingAttachments = new ArrayList<>();
    private final Map<String, AttachmentContextBuilder.AttachmentItem> attachmentStatus = new HashMap<>();
    private boolean attachmentSummaryExpanded = false;
    private boolean attachmentOnlyAnomaly = false;
    private final LocalCliGatewayClient localCliGatewayClient = new LocalCliGatewayClient();
    private WorkFollowupService workFollowupService;

    // Window drag state (for custom window chrome)
    private double windowDragOffsetX = 0;
    private double windowDragOffsetY = 0;
    private boolean windowDragging = false;

    /**
     * Functional interface for the back navigation callback.
     */
    @FunctionalInterface
    public interface BackCallback {
        void onBack();
    }

    @FXML
    public void initialize() {
        // Auto-scroll to bottom when new messages are added
        messagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            chatScrollPane.setVvalue(1.0);
        });

        if (messageInput != null) {
            messageInput.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    if (event.isShiftDown()) {
                        // Shift+Enter: insert newline
                        int caretPos = messageInput.getCaretPosition();
                        messageInput.insertText(caretPos, "\n");
                        event.consume();
                    } else {
                        // Enter: send message
                        event.consume();
                        onSend();
                    }
                }
            });
        }
        
        // Setup toolbar icons
        setupToolbarIcons();
        
        // 强制清除 TextArea 内部子节点背景，确保全域颜色统一
        if (messageInput != null) {
            messageInput.skinProperty().addListener((obs, oldSkin, newSkin) -> {
                if (newSkin != null) {
                    messageInput.lookupAll(".content").forEach(node -> 
                        node.setStyle("-fx-background-color: transparent;"));
                    messageInput.lookupAll(".scroll-pane").forEach(node ->
                        node.setStyle("-fx-background-color: transparent;"));
                }
            });
        }
        
        // Setup draggable input divider
        setupInputDivider();

        // Make chat header draggable (move window)
        setupWindowDrag();
        initQuickReminderControls();
        refreshAttachmentPreview();
    }

    private void initQuickReminderControls() {
        if (quickReminderDayChoice != null) {
            quickReminderDayChoice.getItems().setAll("今天", "明天", "后天");
            quickReminderDayChoice.setValue("明天");
        }
        if (quickReminderTimeChoice != null) {
            List<String> times = new ArrayList<>();
            for (int h = 0; h < 24; h++) {
                times.add(String.format("%02d:00", h));
                times.add(String.format("%02d:30", h));
            }
            quickReminderTimeChoice.getItems().setAll(times);
            quickReminderTimeChoice.setValue("10:00");
        }
        if (quickReminderLeadChoice != null) {
            quickReminderLeadChoice.getItems().setAll(5, 10, 30);
            quickReminderLeadChoice.setValue(5);
        }
    }

    private void setupWindowDrag() {
        if (chatHeaderBar == null) {
            return;
        }

        // Update cursor based on whether we can drag at the pointer
        chatHeaderBar.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (isDragBlockedTarget(e.getTarget())) {
                chatHeaderBar.setCursor(Cursor.DEFAULT);
            } else {
                chatHeaderBar.setCursor(Cursor.OPEN_HAND);
            }
        });

        chatHeaderBar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!e.isPrimaryButtonDown() || isDragBlockedTarget(e.getTarget())) {
                windowDragging = false;
                return;
            }
            Stage stage = getStage();
            if (stage == null) {
                windowDragging = false;
                return;
            }
            windowDragging = true;
            windowDragOffsetX = e.getScreenX() - stage.getX();
            windowDragOffsetY = e.getScreenY() - stage.getY();
            chatHeaderBar.setCursor(Cursor.CLOSED_HAND);
        });

        chatHeaderBar.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!windowDragging) {
                return;
            }
            Stage stage = getStage();
            if (stage == null) {
                return;
            }
            stage.setX(e.getScreenX() - windowDragOffsetX);
            stage.setY(e.getScreenY() - windowDragOffsetY);
        });

        chatHeaderBar.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!windowDragging) {
                return;
            }
            windowDragging = false;
            // Restore cursor (depends on pointer position)
            if (isDragBlockedTarget(e.getTarget())) {
                chatHeaderBar.setCursor(Cursor.DEFAULT);
            } else {
                chatHeaderBar.setCursor(Cursor.OPEN_HAND);
            }
        });
    }

    private Stage getStage() {
        if (chatHeaderBar == null || chatHeaderBar.getScene() == null) {
            return null;
        }
        if (chatHeaderBar.getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return null;
    }

    private boolean isDragBlockedTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        // If clicking on interactive controls, do not start dragging.
        Node cur = node;
        while (cur != null && cur != chatHeaderBar) {
            if (cur instanceof Button
                    || cur instanceof Hyperlink
                    || cur instanceof TextField
                    || cur instanceof TextArea
                    || cur instanceof ChoiceBox<?>
                    || cur instanceof ComboBox<?>
                    || cur instanceof ScrollBar
                    || cur instanceof Slider) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }
    
    /**
     * Sets up the draggable divider for resizing the input area.
     */
    private void setupInputDivider() {
        if (inputDivider == null || inputArea == null) {
            return;
        }
        
        final double[] dragStartY = new double[1];
        final double[] startHeight = new double[1];
        
        inputDivider.setOnMousePressed(event -> {
            dragStartY[0] = event.getScreenY();
            startHeight[0] = inputArea.getPrefHeight();
            event.consume();
        });
        
        inputDivider.setOnMouseDragged(event -> {
            double deltaY = dragStartY[0] - event.getScreenY();
            double newHeight = startHeight[0] + deltaY;
            
            // Clamp between min and max
            double minH = inputArea.getMinHeight();
            double maxH = inputArea.getMaxHeight();
            newHeight = Math.max(minH, Math.min(maxH, newHeight));
            
            inputArea.setPrefHeight(newHeight);
            event.consume();
        });
    }
    
    /**
     * Sets up SVG icons for the chat toolbar buttons.
     */
    private void setupToolbarIcons() {
        // File attach icon (folder/file icon)
        if (attachFileBtn != null) {
            // Folder icon path (Material Design)
            SVGPath folderPath = new SVGPath();
            folderPath.setContent("M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z");
            Region folderIcon = new Region();
            folderIcon.setShape(folderPath);
            folderIcon.setMinSize(18, 18);
            folderIcon.setPrefSize(18, 18);
            folderIcon.setMaxSize(18, 18);
            folderIcon.getStyleClass().add("chat-tool-icon");
            attachFileBtn.setGraphic(folderIcon);
        }
    }

    /**
     * Sets the ChatService dependency.
     */
    public void setChatService(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Sets current login user id for chat session persistence.
     */
    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public void setWorkFollowupService(WorkFollowupService workFollowupService) {
        this.workFollowupService = workFollowupService;
    }

    /**
     * Sets the Skill for this chat session and updates the header.
     */
    public void setSkill(Skill skill) {
        this.currentSkill = skill;
        this.sessionId = UUID.randomUUID().toString();

        this.currentSkillZh = SkillI18nUtil.zhCn(skill);
        String displayName = (currentSkillZh != null && currentSkillZh.displayName() != null)
                ? currentSkillZh.displayName()
                : skill.name();
        skillNameLabel.setText(displayName != null ? displayName : "");

        // Show type badge for internal skills
        if (skill.type() == SkillType.INTERNAL) {
            skillTypeBadge.setText("内部");
            skillTypeBadge.setManaged(true);
            skillTypeBadge.setVisible(true);
        } else {
            skillTypeBadge.setText("通用");
            skillTypeBadge.getStyleClass().setAll("card-category");
            skillTypeBadge.setManaged(true);
            skillTypeBadge.setVisible(true);
        }

        renderHowToPanel(true);
        updateStepPanelVisibility();
        applyMarketplacePreviewMode();
    }

    public void prefillMessage(String message) {
        if (messageInput == null || message == null || message.isBlank()) {
            return;
        }
        messageInput.setText(message);
        messageInput.requestFocus();
        messageInput.positionCaret(messageInput.getText().length());
    }

    @FXML
    public void onToggleHowTo() {
        if (skillHowToPanel == null) {
            return;
        }
        boolean next = !skillHowToPanel.isVisible();
        renderHowToPanel(next);
    }

    private void renderHowToPanel(boolean show) {
        if (skillHowToPanel == null) {
            return;
        }
        if (currentSkill == null || currentSkillZh == null) {
            setHowToVisible(false);
            return;
        }

        if (!show) {
            setHowToVisible(false);
            return;
        }

        String policy = normalizeContextPolicy(currentSkill);
        int scenarioLimit = "minimal".equals(policy) ? 2 : 3;
        int checklistLimit = "minimal".equals(policy) ? 4 : 7;
        int exampleLimit = "advanced".equals(policy) ? 5 : ("minimal".equals(policy) ? 1 : 3);

        if (howToScenariosLabel != null) {
            howToScenariosLabel.setText(formatBullets(currentSkillZh.scenarios(), scenarioLimit));
        }
        if (howToChecklistLabel != null) {
            howToChecklistLabel.setText(formatBullets(currentSkillZh.inputChecklist(), checklistLimit));
        }
        if (howToExamplesRow != null) {
            howToExamplesRow.getChildren().clear();

            List<SkillI18nExample> examples = currentSkillZh.examples() != null ? currentSkillZh.examples() : List.of();
            List<SkillI18nExample> exampleList = new ArrayList<>(examples);
            if (exampleList.isEmpty() && currentSkill.examples() != null) {
                int idx = 1;
                for (String input : currentSkill.examples()) {
                    if (input == null || input.isBlank()) continue;
                    exampleList.add(new SkillI18nExample("示例" + idx, input, null));
                    idx++;
                    if (idx > exampleLimit) break;
                }
            }

            int i = 1;
            for (SkillI18nExample ex : exampleList) {
                if (ex == null || ex.input() == null || ex.input().isBlank()) continue;
                String title = (ex.title() != null && !ex.title().isBlank()) ? ex.title() : ("示例" + i);
                Button btn = new Button(title);
                btn.getStyleClass().add("filter-tag");
                btn.setOnAction(e -> fillInputWithExample(ex.input()));
                howToExamplesRow.getChildren().add(btn);
                i++;
                if (i > exampleLimit) break;
            }
        }
        if (howToOutputFormatLabel != null) {
            String fmt = currentSkillZh.outputFormat();
            if ("minimal".equals(policy)) {
                howToOutputFormatLabel.setText("");
            } else {
                howToOutputFormatLabel.setText((fmt != null && !fmt.isBlank()) ? ("输出预期： " + fmt) : "");
            }
        }

        setHowToVisible(true);
    }

    private void fillInputWithExample(String input) {
        if (messageInput == null) {
            return;
        }
        messageInput.setText(input != null ? input : "");
        messageInput.requestFocus();
        messageInput.positionCaret(messageInput.getText().length());
    }

    private void setHowToVisible(boolean visible) {
        skillHowToPanel.setManaged(visible);
        skillHowToPanel.setVisible(visible);
    }

    private String formatBullets(List<String> items, int maxItems) {
        if (items == null || items.isEmpty()) {
            return "暂无";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String s : items) {
            if (s == null || s.isBlank()) continue;
            if (count >= maxItems) break;
            if (count > 0) sb.append("\n");
            sb.append("• ").append(s.trim());
            count++;
        }
        return count == 0 ? "暂无" : sb.toString();
    }

    /**
     * Sets the callback invoked when the user clicks the back button.
     */
    public void setOnBackCallback(Runnable callback) {
        this.onBackCallback = callback;
    }

    public void setOnHistoryClearedCallback(Runnable callback) {
        this.onHistoryClearedCallback = callback;
    }

    public void setOnUseSkillCallback(Runnable callback) {
        this.onUseSkillCallback = callback;
    }

    public void setInputLocked(boolean locked, String reasonHint) {
        this.inputLocked = locked;
        refreshInputControlsDisabledState();
        if (messageInput != null) {
            if (locked && reasonHint != null && !reasonHint.isBlank()) {
                messageInput.setPromptText(reasonHint);
            } else if (!locked) {
                messageInput.setPromptText("输入消息... (Enter 发送, Shift+Enter 换行)");
            }
        }
    }

    public void setMarketplacePreviewMode(boolean enabled) {
        this.marketplacePreviewMode = enabled;
        applyMarketplacePreviewMode();
    }

    @FXML
    public void onUseSkillNow() {
        if (onUseSkillCallback != null) {
            onUseSkillCallback.run();
        }
    }

    /**
     * Loads existing chat history for the current skill session.
     */
    public void loadHistory(String existingSessionId) {
        if (chatService == null || existingSessionId == null) {
            return;
        }
        this.sessionId = existingSessionId;
        List<ChatMessage> history = chatService.getChatHistory(existingSessionId);
        conversationHistory.clear();
        conversationHistory.addAll(history);
        messagesContainer.getChildren().clear();
        for (ChatMessage msg : history) {
            String role = msg.role();
            String content = msg.content();
            // Filter legacy broken rows that only contain spaces/newlines.
            if ("assistant".equals(role) && (content == null || content.strip().isEmpty())) {
                continue;
            }
            appendMessageBubble(role, content);
        }
    }

    // ---- Event Handlers ----

    @FXML
    public void onBack() {
        if (onBackCallback != null) {
            onBackCallback.run();
        }
    }
    
    /**
     * Handles file attachment button click.
     * Opens a file chooser and attaches the selected file to the chat.
     */
    @FXML
    public void onAttachFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择文件");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("所有文件", "*.*"),
                new FileChooser.ExtensionFilter("文本文件", "*.txt", "*.md", "*.csv"),
                new FileChooser.ExtensionFilter("文档", "*.doc", "*.docx", "*.pdf"),
                new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        List<File> selected = chooser.showOpenMultipleDialog(messageInput.getScene().getWindow());
        if (selected != null && !selected.isEmpty()) {
            for (File file : selected) {
                if (file == null || !file.exists() || !file.isFile()) {
                    continue;
                }
                boolean exists = pendingAttachments.stream()
                        .anyMatch(f -> f != null && f.getAbsolutePath().equals(file.getAbsolutePath()));
                if (!exists) {
                    pendingAttachments.add(file);
                }
            }
            rebuildAttachmentStatus();
            refreshAttachmentPreview();
            messageInput.requestFocus();
            messageInput.positionCaret(messageInput.getText() != null ? messageInput.getText().length() : 0);
        }
    }

    @FXML
    public void onSend() {
        String text = messageInput.getText();
        if ((text == null || text.isBlank()) && pendingAttachments.isEmpty()) {
            return;
        }
        if (sending) {
            return;
        }

        String userMessage = text != null ? text.trim() : "";
        AttachmentContextBuilder.AttachmentPayload payload =
                AttachmentContextBuilder.buildPayload(userMessage, pendingAttachments);
        for (AttachmentContextBuilder.AttachmentItem item : payload.items()) {
            if (item != null && item.file() != null) {
                attachmentStatus.put(item.file().getAbsolutePath(), item);
            }
        }
        String requestMessage = payload.requestMessage();
        String attachmentSuffix = payload.displaySuffix();
        String userDisplayMessage = userMessage.isBlank()
                ? attachmentSuffix.replaceFirst("^\\n+", "")
                : userMessage + attachmentSuffix;
        messageInput.clear();
        hideError();

        // Create and display user message
        ChatMessage userMsg = new ChatMessage(
                UUID.randomUUID().toString(),
                sessionId,
                currentSkill.id(),
                "user",
                userDisplayMessage,
                System.currentTimeMillis()
        );
        conversationHistory.add(userMsg);
        appendMessageBubble("user", userDisplayMessage);

        // Save user message
        if (chatService != null) {
            chatService.saveMessage(userMsg, currentUserId, currentSkill != null ? currentSkill.name() : "Skill");
        }

        // Direct create path for non-technical reminder requests:
        // "一句话 + 时间" should create todo immediately.
        if (pendingAttachments.isEmpty()) {
            ParsedTodoIntent directIntent = parseDirectTodoIntent(userMessage);
            if (directIntent != null) {
                boolean created = createTodoAndReply(directIntent, null);
                if (created) {
                    pendingAttachments.clear();
                    attachmentStatus.clear();
                    refreshAttachmentPreview();
                    return;
                }
            }
        }

        // Send to LLM (streaming)
        setLoading(true);
        resetStepPanelForSending();
        beginStreamingBubble();
        pendingAttachments.clear();
        attachmentStatus.clear();
        refreshAttachmentPreview();
        if (chatService != null) {
            stopRequested = false;
            CompletableFuture<ChatResponse> streamFuture = chatService.sendMessageStream(
                    currentSkill.id(),
                    requestMessage,
                    new ArrayList<>(conversationHistory),
                    delta -> Platform.runLater(() -> appendStreaming(delta))
            );
            streamFuture.thenAccept(response -> Platform.runLater(() -> finalizeStreaming(response)))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> handleError(ex));
                        return null;
                    });
            activeStreamingFuture = streamFuture;
        } else {
            handleError(new IllegalStateException("ChatService not initialized"));
        }
    }

    @FXML
    public void onQuickCreateReminder() {
        if (sending || workFollowupService == null) {
            return;
        }
        String title = messageInput != null && messageInput.getText() != null && !messageInput.getText().isBlank()
                ? messageInput.getText().trim()
                : "待办提醒";
        String day = quickReminderDayChoice != null && quickReminderDayChoice.getValue() != null
                ? quickReminderDayChoice.getValue()
                : "明天";
        String time = quickReminderTimeChoice != null && quickReminderTimeChoice.getValue() != null
                ? quickReminderTimeChoice.getValue()
                : "10:00";
        int lead = quickReminderLeadChoice != null && quickReminderLeadChoice.getValue() != null
                ? quickReminderLeadChoice.getValue()
                : 5;
        LocalDateTime base = LocalDateTime.now();
        if ("明天".equals(day)) {
            base = base.plusDays(1);
        } else if ("后天".equals(day)) {
            base = base.plusDays(2);
        }
        int hour = 10;
        int minute = 0;
        try {
            String[] hm = time.split(":");
            hour = Integer.parseInt(hm[0]);
            minute = Integer.parseInt(hm[1]);
        } catch (Exception ignored) {
            // keep defaults
        }
        LocalDateTime due = LocalDateTime.of(base.toLocalDate(), LocalTime.of(Math.max(0, Math.min(23, hour)),
                Math.max(0, Math.min(59, minute))));
        String note = "来源于快捷提醒创建。[lead=" + lead + "]";
        WorkTodo todo = workFollowupService.createTodo(title, due.format(TODO_DT_FORMATTER), "medium", note);
        if (todo == null) {
            return;
        }
        String reply = "已创建提醒任务：\n"
                + "- 任务ID：" + todo.id() + "\n"
                + "- 标题：" + todo.title() + "\n"
                + "- 截止：" + todo.dueAt() + "\n"
                + "- 通知渠道：Email + 飞书\n"
                + "- 提醒策略：截止前 " + lead + " 分钟";
        appendMessageBubble("assistant", reply);
        ChatMessage aiMsg = new ChatMessage(
                UUID.randomUUID().toString(),
                sessionId,
                currentSkill != null ? currentSkill.id() : "",
                "assistant",
                reply,
                System.currentTimeMillis()
        );
        conversationHistory.add(aiMsg);
        if (chatService != null) {
            chatService.saveMessage(aiMsg, currentUserId, currentSkill != null ? currentSkill.name() : "Skill");
        }
        lastAiContent = reply;
        if (messageInput != null) {
            messageInput.clear();
        }
    }

    @FXML
    public void onShowUsageGuide() {
        if (currentSkill == null) {
            return;
        }

        String policy = normalizeContextPolicy(currentSkill);
        int exampleLimit = "advanced".equals(policy) ? 5 : ("minimal".equals(policy) ? 1 : 3);
        String guide = currentSkill.usageGuide();
        StringBuilder content = new StringBuilder(guide != null && !guide.isBlank() ? guide : "暂无使用说明");

        if (currentSkill.examples() != null && !currentSkill.examples().isEmpty()) {
            List<String> exampleLines = currentSkill.examples().stream()
                    .filter(e -> e != null && !e.isBlank())
                    .limit(exampleLimit)
                    .map(e -> "• " + e)
                    .toList();
            if (!exampleLines.isEmpty()) {
                content.append("\n\n示例输入:\n").append(String.join("\n", exampleLines));
            }
        }

        String typeLabel = currentSkill.type() == SkillType.INTERNAL ? "内部 Skill" : "通用 Skill";
        String kindLabel = currentSkill.kind() != null ?
                switch (currentSkill.kind()) {
                    case PROMPT_ONLY -> "通用型（Prompt 驱动）";
                    case KNOWLEDGE_RAG -> "知识型（知识库 RAG）";
                } : "";
        String policyLabel = switch (policy) {
            case "minimal" -> "基础披露";
            case "advanced" -> "高级披露";
            default -> "标准披露";
        };

        if ("advanced".equals(policy)) {
            String ids = currentSkill.toolIds() == null || currentSkill.toolIds().isEmpty()
                    ? "无"
                    : String.join(", ", currentSkill.toolIds());
            String groups = currentSkill.toolGroups() == null || currentSkill.toolGroups().isEmpty()
                    ? "无"
                    : String.join(", ", currentSkill.toolGroups());
            content.append("\n\n能力编排:\n")
                    .append("• toolIds: ").append(ids)
                    .append("\n• toolGroups: ").append(groups);
        }

        DialogHelper.showInformation("",
            currentSkill.name() + "\n类型: " + typeLabel + " | " + kindLabel + " | " + policyLabel
                    + "\n\n" + content);
    }

    // ---- Internal Methods ----

    private void finalizeStreaming(ChatResponse response) {
        if (stopRequested) {
        setLoading(false);
            activeStreamingFuture = null;
            return;
        }
        setLoading(false);
        activeStreamingFuture = null;

        if (!response.success()) {
            removeStreamingBubble();
            showError(response.errorMessage() != null
                    ? response.errorMessage()
                    : "服务不可用，请稍后重试");
            return;
        }

        String aiContent = streamingBuffer != null ? streamingBuffer.toString() : "";
        if (response.content() != null && !response.content().isBlank()) {
            aiContent = response.content();
        }

        removeStreamingBubble();
        updateStepPanelWithResponse(response);

        ChatMessage aiMsg = new ChatMessage(
                UUID.randomUUID().toString(),
                sessionId,
                currentSkill.id(),
                "assistant",
                aiContent,
                System.currentTimeMillis()
        );
        conversationHistory.add(aiMsg);
        appendMessageBubble("assistant", aiContent);
        lastAiContent = aiContent;

        if (chatService != null) {
            chatService.saveMessage(aiMsg, currentUserId, currentSkill != null ? currentSkill.name() : "Skill");
        }
    }

    private void resetStepPanelForSending() {
        if (stepPanel == null || !isMultiAgentMode()) {
            return;
        }
        stepPanel.setManaged(true);
        stepPanel.setVisible(true);
        planStepBox.setManaged(true);
        planStepBox.setVisible(true);
        executionStepBox.setManaged(true);
        executionStepBox.setVisible(true);
        reviewStepBox.setManaged(true);
        reviewStepBox.setVisible(true);
        planContent.setText("计划生成中...");
        executionContent.setText("执行中...");
        reviewContent.setText("审阅中...");
    }

    private void updateStepPanelWithResponse(ChatResponse response) {
        if (stepPanel == null || !isMultiAgentMode()) {
            return;
        }
        String plan = response.plan();
        String execution = response.execution();
        if (plan == null || plan.isBlank()) {
            planStepBox.setManaged(false);
            planStepBox.setVisible(false);
        } else {
            planStepBox.setManaged(true);
            planStepBox.setVisible(true);
            planContent.setText(plan);
        }

        if (execution == null || execution.isBlank()) {
            executionStepBox.setManaged(false);
            executionStepBox.setVisible(false);
        } else {
            executionStepBox.setManaged(true);
            executionStepBox.setVisible(true);
            executionContent.setText(execution);
        }

        reviewStepBox.setManaged(true);
        reviewStepBox.setVisible(true);
        reviewContent.setText(response.content() != null ? response.content() : "");
    }

    private void hideStepPanel() {
        if (stepPanel == null) {
            return;
        }
        stepPanel.setManaged(false);
        stepPanel.setVisible(false);
    }

    private void updateStepPanelVisibility() {
        if (isMultiAgentMode()) {
            stepPanel.setManaged(true);
            stepPanel.setVisible(true);
        } else {
            hideStepPanel();
        }
    }

    private boolean isMultiAgentMode() {
        return currentSkill != null && currentSkill.executionMode() == SkillExecutionMode.MULTI;
    }

    private void handleError(Throwable ex) {
        if (stopRequested) {
            return;
        }
        setLoading(false);
        activeStreamingFuture = null;
        LOGGER.log(Level.WARNING, "Chat request failed", ex);
        removeStreamingBubble();
        showError("请求失败，请检查网络连接后重试");
    }

    /**
     * Appends a chat bubble to the messages container.
     * User messages are right-aligned with blue background.
     * AI messages are left-aligned with gray background and Markdown rendering.
     */
    void appendMessageBubble(String role, String content) {
        if ("assistant".equals(role) && (content == null || content.strip().isEmpty())) {
            return;
        }
        HBox row = new HBox();
        row.setPadding(new Insets(2, 0, 2, 0));

        if ("user".equals(role)) {
            javafx.scene.Node bubble = createSelectablePlainBubble(content, "chat-bubble-user", 500);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row.getChildren().addAll(spacer, bubble);
            row.setAlignment(Pos.CENTER_RIGHT);
        } else {
            // AI response - render Markdown as HTML in WebView
            javafx.scene.Node bubbleNode = createAiBubble(content);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row.getChildren().addAll(bubbleNode, spacer);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        messagesContainer.getChildren().add(row);
        ensureScrollToBottom();
    }

    /**
     * Creates a selectable plain-text bubble that supports drag selection and native right-click copy.
     */
    private javafx.scene.Node createSelectablePlainBubble(String content, String styleClass, double maxWidth) {
        TextArea textArea = new TextArea(content == null ? "" : content);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setFocusTraversable(true);
        textArea.setMaxWidth(maxWidth);
        textArea.getStyleClass().add("chat-bubble-plain-text");
        textArea.setPrefRowCount(1);
        textArea.setMinHeight(Region.USE_PREF_SIZE);
        textArea.setCursor(javafx.scene.Cursor.TEXT);
        // Workaround for occasional JavaFX skin classloading bug on macOS:
        // triple-click on TextArea may trigger TextAreaSkin.moveCaret() and crash.
        textArea.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() >= 3) {
                event.consume();
            }
        });

        // Conservative height estimate to avoid inner scrollbars and preserve bubble shape.
        int linesByNewline = Math.max(1, (content == null ? "" : content).split("\n", -1).length);
        int approxChars = content == null ? 0 : content.length();
        int linesByWrap = Math.max(1, (int) Math.ceil(approxChars / 22.0));
        int lines = Math.max(linesByNewline, linesByWrap);
        lines = Math.min(lines, 24);
        textArea.setPrefHeight(18 + lines * 20);
        textArea.setMaxHeight(Region.USE_PREF_SIZE);
        clearBubbleTextAreaBackground(textArea);
        installBubbleContextMenu(textArea);

        StackPane bubble = new StackPane(textArea);
        bubble.setMaxWidth(maxWidth);
        bubble.getStyleClass().add(styleClass);
        return bubble;
    }

    /**
     * Creates an AI response bubble.
     * Use plain selectable bubble for stability (avoids WebView blank rendering).
     */
    private javafx.scene.Node createAiBubble(String content) {
        if (content == null || content.isBlank()) {
            return createSelectablePlainBubble("(空回复)", "chat-bubble-ai", 520);
        }
        javafx.scene.Node textBubble = createSelectablePlainBubble(content, "chat-bubble-ai", 520);
        List<String> cliCommands = extractCliCommands(content, 3);
        List<CitationSource> citationSources = extractCitationSources(content, 6);
        boolean needConfirmAction = shouldShowConfirmAction(content);
        if (citationSources.isEmpty() && cliCommands.isEmpty() && !needConfirmAction) {
            return textBubble;
        }
        VBox wrapper = new VBox(6);
        wrapper.setAlignment(Pos.CENTER_LEFT);
        wrapper.getChildren().add(textBubble);
        if (!citationSources.isEmpty()) {
            VBox cardBox = new VBox(4);
            cardBox.getStyleClass().add("citation-cards");
            int index = 1;
            for (CitationSource source : citationSources) {
                VBox card = new VBox(4);
                card.getStyleClass().add("citation-card");
                HBox header = new HBox(6);
                header.setAlignment(Pos.CENTER_LEFT);
                Label tag = new Label("来源 " + index);
                tag.getStyleClass().add("citation-tag");
                Label domain = new Label(source.domain());
                domain.getStyleClass().add("citation-domain");
                header.getChildren().addAll(tag, domain);
                if (source.confidence() != null && !source.confidence().isBlank()) {
                    Label confidence = new Label("置信度 " + source.confidence());
                    confidence.getStyleClass().add("citation-confidence");
                    header.getChildren().add(confidence);
                }
                if (source.fetchedAt() != null && !source.fetchedAt().isBlank()) {
                    Label fetchedAt = new Label("抓取于 " + source.fetchedAt());
                    fetchedAt.getStyleClass().add("citation-time");
                    header.getChildren().add(fetchedAt);
                }

                Hyperlink titleLink = new Hyperlink(source.title());
                titleLink.getStyleClass().add("citation-title");
                titleLink.setOnAction(e -> openExternalUrl(source.url()));
                card.getChildren().addAll(header, titleLink);
                if (source.snippet() != null && !source.snippet().isBlank()) {
                    Label snippet = new Label(source.snippet());
                    snippet.setWrapText(true);
                    snippet.getStyleClass().add("citation-snippet");
                    card.getChildren().add(snippet);
                }
                HBox verifyRow = new HBox(6);
                verifyRow.setAlignment(Pos.CENTER_LEFT);
                Button verifyBtn = new Button("重新验证");
                verifyBtn.getStyleClass().add("citation-verify-btn");
                Label verifyStatus = new Label("");
                verifyStatus.getStyleClass().add("citation-verify-status");
                verifyBtn.setOnAction(e -> revalidateSource(source, verifyStatus, verifyBtn));
                verifyRow.getChildren().addAll(verifyBtn, verifyStatus);
                card.getChildren().add(verifyRow);
                cardBox.getChildren().add(card);
                index++;
            }
            Button copyEvidenceBtn = new Button("复制证据列表");
            copyEvidenceBtn.getStyleClass().add("citation-copy-btn");
            copyEvidenceBtn.setOnAction(e -> copyEvidenceSources(citationSources));
            cardBox.getChildren().add(copyEvidenceBtn);
            wrapper.getChildren().add(cardBox);
        }
        if (!cliCommands.isEmpty()) {
            wrapper.getChildren().add(buildCliActionCards(cliCommands));
        }
        if (needConfirmAction) {
            wrapper.getChildren().add(buildConfirmActionCard(content));
        }
        return wrapper;
    }

    private VBox buildCliActionCards(List<String> commands) {
        VBox box = new VBox(6);
        box.getStyleClass().add("citation-cards");
        int idx = 1;
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            VBox card = new VBox(6);
            card.getStyleClass().add("citation-card");
            Label title = new Label("命令建议 " + idx);
            title.getStyleClass().add("citation-domain");
            Label cmd = new Label(command);
            cmd.setWrapText(true);
            cmd.getStyleClass().add("citation-snippet");
            Label status = new Label("");
            status.getStyleClass().add("citation-verify-status");
            HBox actions = new HBox(6);
            Button dryRunBtn = new Button("预执行");
            dryRunBtn.getStyleClass().add("attachment-summary-action-btn");
            Button runBtn = new Button("执行");
            runBtn.getStyleClass().add("attachment-summary-action-btn");
            Button copyBtn = new Button("复制");
            copyBtn.getStyleClass().add("attachment-summary-action-btn");
            dryRunBtn.setOnAction(e -> {
                String result = localCliGatewayClient.exec(command, "", 30, true, false);
                status.setText(trimForDisplay(formatCliExecFeedback(result, true), 220));
            });
            runBtn.setOnAction(e -> {
                var confirm = DialogHelper.showConfirmation("",
                        "即将执行本机命令，是否继续？\n\n" + trimForDisplay(command, 200));
                if (confirm.isEmpty() || confirm.get() != javafx.scene.control.ButtonType.OK) {
                    status.setText("已取消执行");
                    return;
                }
                String ticketResp = localCliGatewayClient.issueConfirmTicket(command, "");
                String ticket = extractConfirmTicket(ticketResp);
                String result = localCliGatewayClient.exec(command, "", 60, false, true, ticket);
                status.setText(trimForDisplay(formatCliExecFeedback(result, false), 220));
            });
            copyBtn.setOnAction(e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(command);
                Clipboard.getSystemClipboard().setContent(cc);
                status.setText("命令已复制");
            });
            actions.getChildren().addAll(dryRunBtn, runBtn, copyBtn);
            card.getChildren().addAll(title, cmd, actions, status);
            box.getChildren().add(card);
            idx++;
        }
        return box;
    }

    private String formatCliExecFeedback(String json, boolean dryRun) {
        if (json == null || json.isBlank()) {
            return dryRun ? "预执行完成（无回执）" : "执行完成（无回执）";
        }
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            boolean success = o.has("success") && !o.get("success").isJsonNull() && o.get("success").getAsBoolean();
            boolean executed = o.has("executed") && !o.get("executed").isJsonNull() && o.get("executed").getAsBoolean();
            String message = o.has("message") && !o.get("message").isJsonNull() ? o.get("message").getAsString() : "";
            int exit = o.has("exitCode") && !o.get("exitCode").isJsonNull() ? o.get("exitCode").getAsInt() : -1;
            String stderr = o.has("stderr") && !o.get("stderr").isJsonNull() ? o.get("stderr").getAsString() : "";
            String stdout = o.has("stdout") && !o.get("stdout").isJsonNull() ? o.get("stdout").getAsString() : "";
            if (dryRun) {
                return "预执行完成｜风险已评估｜" + blankAs(message, "可执行");
            }
            if (success) {
                String out = firstLine(stdout);
                return "执行成功（exit=" + exit + "）｜" + blankAs(message, "已在本机执行")
                        + (out.isBlank() ? "" : "｜输出: " + out);
            }
            String err = firstLine(stderr);
            return (executed ? "执行失败" : "未执行")
                    + "（exit=" + exit + "）｜"
                    + blankAs(message, "请检查命令与环境")
                    + (err.isBlank() ? "" : "｜错误: " + err);
        } catch (Exception e) {
            return dryRun ? "预执行回执解析失败" : "执行回执解析失败";
        }
    }

    private String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] parts = text.trim().split("\\R");
        return parts.length == 0 ? "" : trimForDisplay(parts[0], 80);
    }

    private String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String extractConfirmTicket(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("ticket") && !o.get("ticket").isJsonNull()) {
                return o.get("ticket").getAsString();
            }
        } catch (Exception ignored) {
            // fallback to empty ticket
        }
        return "";
    }

    private boolean shouldShowConfirmAction(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String t = content.toLowerCase(Locale.ROOT);
        return t.contains("请确认")
                || t.contains("是否继续执行")
                || t.contains("确认后我立即")
                || t.contains("是否继续");
    }

    private VBox buildConfirmActionCard(String sourceContent) {
        VBox card = new VBox(6);
        card.getStyleClass().add("citation-card");
        Label title = new Label("执行确认");
        title.getStyleClass().add("citation-domain");
        Label desc = new Label("点击“确认并继续”后，我会继续推动执行并返回结果。");
        desc.setWrapText(true);
        desc.getStyleClass().add("citation-snippet");
        Label status = new Label("");
        status.getStyleClass().add("citation-verify-status");

        HBox actions = new HBox(6);
        Button confirmBtn = new Button("确认并继续");
        confirmBtn.getStyleClass().add("btn-primary");
        Button cancelBtn = new Button("暂不执行");
        cancelBtn.getStyleClass().add("attachment-summary-action-btn");
        confirmBtn.setOnAction(e -> {
            boolean created = tryCreateTodoDirectlyFromContent(sourceContent, status);
            if (!created) {
                sendQuickMessage("我确认，继续执行并直接在本机落地，完成后给我结果回执。", status);
            } else {
                confirmBtn.setDisable(true);
                confirmBtn.setText("已创建");
            }
        });
        cancelBtn.setOnAction(e -> status.setText("已取消本次执行"));
        actions.getChildren().addAll(confirmBtn, cancelBtn);

        card.getChildren().addAll(title, desc, actions, status);
        VBox box = new VBox(6);
        box.getStyleClass().add("citation-cards");
        box.getChildren().add(card);
        return box;
    }

    private void sendQuickMessage(String message, Label statusLabel) {
        if (sending) {
            if (statusLabel != null) {
                statusLabel.setText("当前正在执行，请稍候");
            }
            return;
        }
        if (messageInput == null) {
            if (statusLabel != null) {
                statusLabel.setText("输入框未初始化");
            }
            return;
        }
        messageInput.setText(message == null ? "" : message);
        onSend();
        if (statusLabel != null) {
            statusLabel.setText("已发送确认指令，正在执行...");
        }
    }

    private boolean tryCreateTodoDirectlyFromContent(String content, Label statusLabel) {
        if (workFollowupService == null || content == null || content.isBlank()) {
            return false;
        }
        ParsedTodoIntent intent = parseTodoIntent(content);
        if (intent == null) {
            return false;
        }
        return createTodoAndReply(intent, statusLabel);
    }

    private boolean createTodoAndReply(ParsedTodoIntent intent, Label statusLabel) {
        if (workFollowupService == null || intent == null || intent.title() == null || intent.title().isBlank()) {
            return false;
        }
        String dueAt = intent.dueAt() != null && !intent.dueAt().isBlank()
                ? intent.dueAt()
                : LocalDateTime.now().plusHours(24).format(TODO_DT_FORMATTER);
        WorkTodo todo = workFollowupService.createTodo(
                intent.title(),
                dueAt,
                intent.priority(),
                intent.note()
        );
        if (todo == null) {
            return false;
        }
        String reply = "已为你在本机创建任务：\n"
                + "- 任务ID：" + todo.id() + "\n"
                + "- 标题：" + todo.title() + "\n"
                + "- 截止：" + (todo.dueAt() == null || todo.dueAt().isBlank() ? "未设置" : todo.dueAt()) + "\n"
                + "- 优先级：" + (todo.priority() == null ? "medium" : todo.priority()) + "\n"
                + "- 通知渠道：Email + 飞书\n"
                + "- 提醒策略：截止前 " + intent.leadMinutes() + " 分钟";
        appendMessageBubble("assistant", reply);
        if (statusLabel != null) {
            statusLabel.setText("任务已创建并进入跟进");
        }
        lastAiContent = reply;
        return true;
    }

    private ParsedTodoIntent parseDirectTodoIntent(String userInput) {
        if (userInput == null || userInput.isBlank() || workFollowupService == null) {
            return null;
        }
        String text = userInput.trim();
        boolean looksLikeTodo = text.contains("提醒")
                || text.contains("待办")
                || text.contains("跟进")
                || text.contains("记得");
        if (!looksLikeTodo) {
            return null;
        }
        String duePhrase = extractDuePhrase(text);
        if (duePhrase == null || duePhrase.isBlank()) {
            return null;
        }
        String dueAt = normalizeDueAt(duePhrase);
        if (dueAt == null || dueAt.isBlank()) {
            return null;
        }
        int leadMinutes = resolveLeadMinutesFromContent(text);
        String title = extractDirectTodoTitle(text, duePhrase);
        if (title == null || title.isBlank()) {
            title = "待办事项";
        }
        String note = "来源于一句话创建。[lead=" + leadMinutes + "]";
        return new ParsedTodoIntent(title, "medium", dueAt, note, leadMinutes);
    }

    private ParsedTodoIntent parseTodoIntent(String content) {
        String title = extractFirst(content,
                "(?m)^[\\-•\\s]*任务\\s*[:：]\\s*(.+)$",
                "(?m)^###\\s*请确认[^\\n]*任务\\s*[:：]\\s*(.+)$");
        String priorityRaw = extractFirst(content, "(?m)^[\\-•\\s]*优先级\\s*[:：]\\s*(.+)$");
        String dueRaw = extractFirst(content,
                "(?m)^[\\-•\\s]*截止时间\\s*[:：]\\s*(.+)$",
                "(?m)^[\\-•\\s]*提醒时间\\s*[:：]\\s*(.+)$");
        if (title == null || title.isBlank()) {
            return null;
        }
        String dueAt = normalizeDueAt(dueRaw);
        String priority = normalizePriority(priorityRaw);
        int leadMinutes = resolveLeadMinutesFromContent(content);
        String note = "来源于确认执行卡片，系统已自动落地创建。[lead=" + leadMinutes + "]";
        return new ParsedTodoIntent(title.trim(), priority, dueAt, note, leadMinutes);
    }

    private String extractFirst(String text, String... patterns) {
        if (text == null || patterns == null) {
            return null;
        }
        for (String p : patterns) {
            if (p == null || p.isBlank()) {
                continue;
            }
            Matcher m = Pattern.compile(p).matcher(text);
            if (m.find()) {
                String value = m.groupCount() >= 1 ? m.group(1) : null;
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private String normalizePriority(String raw) {
        String p = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (p.contains("高") || p.contains("high")) {
            return "high";
        }
        if (p.contains("低") || p.contains("low")) {
            return "low";
        }
        return "medium";
    }

    private String normalizeDueAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = raw.trim();
        try {
            return LocalDateTime.parse(t, TODO_DT_FORMATTER).format(TODO_DT_FORMATTER);
        } catch (Exception ignored) {
            // continue
        }
        Matcher hm = Pattern.compile("(\\d{1,2})[:：](\\d{2})").matcher(t);
        Matcher dot = Pattern.compile("(\\d{1,2})点(?:(\\d{1,2})分?)?").matcher(t);
        LocalDateTime base = LocalDateTime.now();
        if (t.contains("明天")) {
            base = base.plusDays(1);
        } else if (t.contains("后天")) {
            base = base.plusDays(2);
        }
        int hour = -1;
        int minute = 0;
        if (hm.find()) {
            hour = Integer.parseInt(hm.group(1));
            minute = Integer.parseInt(hm.group(2));
        } else if (dot.find()) {
            hour = Integer.parseInt(dot.group(1));
            minute = dot.group(2) == null ? 0 : Integer.parseInt(dot.group(2));
        }
        if (hour >= 0) {
            if ((t.contains("下午") || t.contains("晚上")) && hour < 12) {
                hour += 12;
            }
            LocalDateTime dt = LocalDateTime.of(base.toLocalDate(), LocalTime.of(Math.max(0, Math.min(23, hour)),
                    Math.max(0, Math.min(59, minute))));
            return dt.format(TODO_DT_FORMATTER);
        }
        return "";
    }

    private String extractDuePhrase(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher m = Pattern.compile("(今天|明天|后天)?\\s*(上午|中午|下午|晚上)?\\s*\\d{1,2}(?:[:：]\\d{2}|点(?:\\d{1,2}分?)?)")
                .matcher(text);
        if (m.find()) {
            return m.group().trim();
        }
        return "";
    }

    private String extractDirectTodoTitle(String text, String duePhrase) {
        if (text == null) {
            return "";
        }
        String title = text;
        if (duePhrase != null && !duePhrase.isBlank()) {
            title = title.replace(duePhrase, " ");
        }
        title = title.replaceAll("提前\\s*\\d{1,3}\\s*分钟", " ");
        title = title.replaceAll("(?i)\\[lead=\\d{1,3}\\]", " ");
        title = title.replaceAll("^(请)?(帮我|给我|麻烦)?", "");
        title = title.replaceAll("(创建|新增|设置|加一个)?\\s*(提醒我|提醒|待办|跟进|记得)", "");
        title = title.replaceAll("[，。,.；;!！]+", " ").trim();
        return title;
    }

    private int resolveLeadMinutesFromContent(String content) {
        String text = content == null ? "" : content;
        Matcher m = Pattern.compile("提前\\s*(\\d{1,3})\\s*分钟").matcher(text);
        if (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                return Math.max(1, Math.min(180, v));
            } catch (Exception ignored) {
                return 5;
            }
        }
        return 5;
    }

    private record ParsedTodoIntent(String title, String priority, String dueAt, String note, int leadMinutes) {
    }

    private List<String> extractCliCommands(String content, int maxCount) {
        if (content == null || content.isBlank() || maxCount <= 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String[] lines = content.split("\\R");
        boolean inShellBlock = false;
        StringBuilder block = new StringBuilder();
        for (String line : lines) {
            String t = line == null ? "" : line.trim();
            if (t.startsWith("```")) {
                if (!inShellBlock && (t.equals("```shell") || t.equals("```bash") || t.equals("```zsh")
                        || t.equals("```powershell") || t.equals("```ps1"))) {
                    inShellBlock = true;
                    block.setLength(0);
                    continue;
                }
                if (inShellBlock) {
                    String cmd = block.toString().trim();
                    if (!cmd.isBlank()) {
                        out.add(cmd);
                        if (out.size() >= maxCount) {
                            return out;
                        }
                    }
                    inShellBlock = false;
                    block.setLength(0);
                }
                continue;
            }
            if (inShellBlock) {
                block.append(line).append('\n');
                continue;
            }
            if (t.startsWith("CLI_COMMAND:")) {
                String cmd = t.substring("CLI_COMMAND:".length()).trim();
                if (!cmd.isBlank()) {
                    out.add(cmd);
                    if (out.size() >= maxCount) {
                        return out;
                    }
                }
            }
        }
        return out;
    }

    private List<CitationSource> extractCitationSources(String text, int maxCount) {
        if (text == null || text.isBlank() || maxCount <= 0) {
            return List.of();
        }
        List<CitationSource> sources = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length && sources.size() < maxCount; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isBlank()) {
                continue;
            }
            Matcher matcher = CITATION_URL_PATTERN.matcher(line);
            while (matcher.find() && sources.size() < maxCount) {
                String raw = matcher.group();
                String normalizedUrl = normalizeCitationUrl(raw);
                if (normalizedUrl.isBlank() || seen.contains(normalizedUrl)) {
                    continue;
                }
                seen.add(normalizedUrl);
                String title = findCitationTitle(lines, i, normalizedUrl);
                String snippet = findCitationSnippet(lines, i);
                String confidence = findCitationConfidence(lines, i);
                String fetchedAt = findCitationFetchedAt(lines, i);
                sources.add(new CitationSource(
                        normalizedUrl,
                        title,
                        extractDomain(normalizedUrl),
                        snippet,
                        confidence,
                        fetchedAt
                ));
            }
        }
        return sources;
    }

    private String normalizeCitationUrl(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("[),.;!]+$", "");
    }

    private String findCitationTitle(String[] lines, int lineIndex, String url) {
        String currentLine = lines[lineIndex] == null ? "" : lines[lineIndex].trim();
        boolean pureUrlLine = currentLine.matches("(?i)^url\\s*[:：].*");
        if (!pureUrlLine && !currentLine.equals(url)) {
            String titleInSameLine = currentLine
                    .replaceFirst("(?i)^\\d+\\.\\s*", "")
                    .replace(url, "")
                    .replaceFirst("(?i)url\\s*[:：]", "")
                    .trim();
            if (!titleInSameLine.isBlank()) {
                return trimForDisplay(titleInSameLine, 90);
            }
        }
        for (int i = lineIndex - 1; i >= 0 && i >= lineIndex - 3; i--) {
            String prev = lines[i] == null ? "" : lines[i].trim();
            if (prev.isBlank()) {
                continue;
            }
            Matcher m = NUMBERED_TITLE_PATTERN.matcher(prev);
            if (m.find()) {
                return trimForDisplay(m.group(1), 90);
            }
            if (!prev.toLowerCase(Locale.ROOT).startsWith("url:")
                    && !prev.startsWith("URL:")
                    && !prev.startsWith("摘要:")
                    && !prev.startsWith("证据")) {
                return trimForDisplay(prev, 90);
            }
        }
        return trimForDisplay(url, 90);
    }

    private String findCitationSnippet(String[] lines, int lineIndex) {
        for (int i = lineIndex + 1; i < lines.length && i <= lineIndex + 3; i++) {
            String next = lines[i] == null ? "" : lines[i].trim();
            if (next.isBlank()) {
                continue;
            }
            if (next.startsWith("摘要:") || next.startsWith("摘要：")) {
                return trimForDisplay(next.replaceFirst("^摘要[:：]\\s*", ""), 120);
            }
            if (!next.startsWith("URL:") && !next.startsWith("证据")) {
                return trimForDisplay(next, 120);
            }
        }
        return "";
    }

    private String findCitationConfidence(String[] lines, int lineIndex) {
        for (int i = lineIndex; i < lines.length && i <= lineIndex + 3; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.startsWith("置信度:") || line.startsWith("置信度：")) {
                String value = line.replaceFirst("^置信度[:：]\\s*", "").trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "中";
    }

    private String findCitationFetchedAt(String[] lines, int lineIndex) {
        for (int i = lineIndex; i < lines.length && i <= lineIndex + 4; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.startsWith("抓取时间:") || line.startsWith("抓取时间：")) {
                String value = line.replaceFirst("^抓取时间[:：]\\s*", "").trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private String trimForDisplay(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen) + "...";
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "未知来源";
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ignored) {
            return "未知来源";
        }
    }

    private void openExternalUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                DialogHelper.showInformation("", "当前环境不支持打开链接");
                return;
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open citation url: " + url, e);
            DialogHelper.showInformation("", "打开链接失败，请稍后重试");
        }
    }

    private void copyEvidenceSources(List<CitationSource> sources) {
        if (sources == null || sources.isEmpty()) {
            DialogHelper.showInformation("", "暂无可复制证据");
            return;
        }
        StringBuilder sb = new StringBuilder("证据列表\n");
        int i = 1;
        for (CitationSource source : sources) {
            sb.append(i).append(". ").append(source.title()).append("\n")
                    .append("来源: ").append(source.domain()).append("\n")
                    .append("URL: ").append(source.url()).append("\n")
                    .append("置信度: ").append(source.confidence()).append("\n");
            if (source.fetchedAt() != null && !source.fetchedAt().isBlank()) {
                sb.append("抓取时间: ").append(source.fetchedAt()).append("\n");
            }
            if (source.snippet() != null && !source.snippet().isBlank()) {
                sb.append("摘要: ").append(source.snippet()).append("\n");
            }
            sb.append("\n");
            i++;
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(sb.toString().trim());
        Clipboard.getSystemClipboard().setContent(cc);
        DialogHelper.showInformation("", "证据列表已复制");
    }

    private void revalidateSource(CitationSource source, Label statusLabel, Button verifyBtn) {
        if (source == null || source.url() == null || source.url().isBlank()) {
            return;
        }
        if (statusLabel != null) {
            statusLabel.setText("验证中...");
        }
        if (verifyBtn != null) {
            verifyBtn.setDisable(true);
        }
        CompletableFuture.supplyAsync(() -> checkUrlAvailability(source.url()))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText(result);
                    }
                    if (verifyBtn != null) {
                        verifyBtn.setDisable(false);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        if (statusLabel != null) {
                            statusLabel.setText("验证失败");
                        }
                        if (verifyBtn != null) {
                            verifyBtn.setDisable(false);
                        }
                    });
                    return null;
                });
    }

    private String checkUrlAvailability(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "EverythingAssistant/1.0")
                    .GET()
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            int code = resp.statusCode();
            if (code >= 200 && code < 400) {
                return "可访问 (" + code + ")";
            }
            return "不可用 (" + code + ")";
        } catch (Exception e) {
            return "不可访问";
        }
    }

    private record CitationSource(String url, String title, String domain, String snippet, String confidence,
                                  String fetchedAt) {
    }

    private void setLoading(boolean loading) {
        this.sending = loading;
        loadingBox.setManaged(loading);
        loadingBox.setVisible(loading);
        refreshInputControlsDisabledState();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void refreshInputControlsDisabledState() {
        boolean disabled = sending || inputLocked;
        if (messageInput != null) {
            messageInput.setDisable(disabled);
        }
        if (sendBtn != null) {
            sendBtn.setDisable(disabled);
        }
        if (attachFileBtn != null) {
            attachFileBtn.setDisable(disabled);
        }
        if (quickReminderCreateBtn != null) {
            quickReminderCreateBtn.setDisable(disabled);
        }
        if (quickReminderDayChoice != null) {
            quickReminderDayChoice.setDisable(disabled);
        }
        if (quickReminderTimeChoice != null) {
            quickReminderTimeChoice.setDisable(disabled);
        }
        if (quickReminderLeadChoice != null) {
            quickReminderLeadChoice.setDisable(disabled);
        }
        if (stopBtn != null) {
            stopBtn.setDisable(!sending);
        }
    }

    private void applyMarketplacePreviewMode() {
        if (inputArea != null) {
            inputArea.setManaged(!marketplacePreviewMode);
            inputArea.setVisible(!marketplacePreviewMode);
        }
        if (inputDivider != null) {
            inputDivider.setManaged(!marketplacePreviewMode);
            inputDivider.setVisible(!marketplacePreviewMode);
        }
        if (chatScrollPane != null) {
            chatScrollPane.setManaged(!marketplacePreviewMode);
            chatScrollPane.setVisible(!marketplacePreviewMode);
        }
        if (marketplaceActionBar != null) {
            marketplaceActionBar.setManaged(marketplacePreviewMode);
            marketplaceActionBar.setVisible(marketplacePreviewMode);
        }
        if (marketplacePreviewMode) {
            // In marketplace preview, show usage/how-to content by default.
            renderHowToPanel(true);
        }
    }

    private void refreshAttachmentPreview() {
        if (attachmentStrip == null || attachmentRow == null) {
            return;
        }
        attachmentRow.getChildren().clear();
        if (pendingAttachments.isEmpty()) {
            attachmentStrip.setManaged(false);
            attachmentStrip.setVisible(false);
            attachmentSummaryExpanded = false;
            attachmentOnlyAnomaly = false;
            if (attachmentBatchSummaryBox != null) {
                attachmentBatchSummaryBox.setManaged(false);
                attachmentBatchSummaryBox.setVisible(false);
            }
            if (attachmentBatchDetailLabel != null) {
                attachmentBatchDetailLabel.setManaged(false);
                attachmentBatchDetailLabel.setVisible(false);
                attachmentBatchDetailLabel.setText("");
            }
            return;
        }
        for (File file : pendingAttachments) {
            HBox chip = new HBox(4);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("attachment-chip");

            Label name = new Label(file.getName());
            name.getStyleClass().add("attachment-chip-label");
            name.setMaxWidth(180);
            name.setEllipsisString("...");
            HBox.setHgrow(name, Priority.NEVER);

            Label statusLabel = new Label(resolveAttachmentStatus(file));
            statusLabel.getStyleClass().add("attachment-chip-status");
            statusLabel.setMaxWidth(120);

            Button remove = new Button("×");
            remove.getStyleClass().add("attachment-chip-remove");
            remove.setOnAction(e -> {
                pendingAttachments.remove(file);
                attachmentStatus.remove(file.getAbsolutePath());
                refreshAttachmentPreview();
            });
            AttachmentContextBuilder.AttachmentItem item = attachmentStatus.get(file.getAbsolutePath());
            if (item != null && item.detail() != null && !item.detail().isBlank()) {
                Tooltip.install(chip, new Tooltip(item.detail()));
            }
            chip.getChildren().addAll(name, statusLabel, remove);
            attachmentRow.getChildren().add(chip);
        }
        attachmentStrip.setManaged(true);
        attachmentStrip.setVisible(true);
        refreshAttachmentBatchSummary();
    }

    private void rebuildAttachmentStatus() {
        attachmentStatus.clear();
        AttachmentContextBuilder.AttachmentPayload payload =
                AttachmentContextBuilder.buildPayload("", pendingAttachments);
        for (AttachmentContextBuilder.AttachmentItem item : payload.items()) {
            if (item != null && item.file() != null) {
                attachmentStatus.put(item.file().getAbsolutePath(), item);
            }
        }
    }

    private String resolveAttachmentStatus(File file) {
        if (file == null) {
            return "";
        }
        AttachmentContextBuilder.AttachmentItem item = attachmentStatus.get(file.getAbsolutePath());
        if (item == null || item.status() == null || item.status().isBlank()) {
            return "待解析";
        }
        return item.status();
    }

    private void refreshAttachmentBatchSummary() {
        if (attachmentBatchSummaryBox == null || attachmentBatchSummaryLabel == null) {
            return;
        }
        if (pendingAttachments.isEmpty()) {
            attachmentBatchSummaryBox.setManaged(false);
            attachmentBatchSummaryBox.setVisible(false);
            return;
        }
        int total = pendingAttachments.size();
        int truncated = 0;
        int unsupported = 0;
        Map<String, AttachmentInsight> insightMap = buildAttachmentInsightMap();
        int anomaly = 0;
        for (File file : pendingAttachments) {
            if (file == null) {
                continue;
            }
            AttachmentInsight insight = insightMap.get(file.getAbsolutePath());
            if (insight != null && insight.hasAnomaly()) {
                anomaly++;
            }
            AttachmentContextBuilder.AttachmentItem item = attachmentStatus.get(file.getAbsolutePath());
            if (item == null || item.status() == null) {
                continue;
            }
            String s = item.status();
            if (s.contains("截断")) {
                truncated++;
            }
            if (s.contains("未支持")) {
                unsupported++;
            }
        }
        String summary = "批量总览：已选 " + total + " 个附件"
                + "，异常提示 " + anomaly + " 个"
                + "，截断 " + truncated + " 个"
                + "，未支持 " + unsupported + " 个。";
        attachmentBatchSummaryLabel.setText(summary);
        if (attachmentSummaryToggleBtn != null) {
            attachmentSummaryToggleBtn.setText(attachmentSummaryExpanded ? "收起明细" : "展开明细");
        }
        if (attachmentSummaryActionRow != null) {
            attachmentSummaryActionRow.setManaged(true);
            attachmentSummaryActionRow.setVisible(true);
        }
        if (attachmentAnomalyFilterBtn != null) {
            attachmentAnomalyFilterBtn.setText(attachmentOnlyAnomaly ? "查看全部附件" : "仅看异常项");
        }
        if (attachmentCopyAnomalyBtn != null) {
            attachmentCopyAnomalyBtn.setDisable(anomaly == 0);
        }
        if (attachmentBatchDetailLabel != null) {
            if (attachmentSummaryExpanded) {
                attachmentBatchDetailLabel.setText(buildAttachmentDetailText(insightMap, attachmentOnlyAnomaly));
                attachmentBatchDetailLabel.setManaged(true);
                attachmentBatchDetailLabel.setVisible(true);
            } else {
                attachmentBatchDetailLabel.setManaged(false);
                attachmentBatchDetailLabel.setVisible(false);
                attachmentBatchDetailLabel.setText("");
            }
        }
        attachmentBatchSummaryBox.setManaged(true);
        attachmentBatchSummaryBox.setVisible(true);
    }

    @FXML
    public void onToggleAttachmentSummaryDetail() {
        attachmentSummaryExpanded = !attachmentSummaryExpanded;
        refreshAttachmentBatchSummary();
    }

    @FXML
    public void onToggleAttachmentAnomalyOnly() {
        attachmentOnlyAnomaly = !attachmentOnlyAnomaly;
        attachmentSummaryExpanded = true;
        refreshAttachmentBatchSummary();
    }

    @FXML
    public void onCopyAttachmentAnomalyList() {
        Map<String, AttachmentInsight> insightMap = buildAttachmentInsightMap();
        String text = buildAttachmentAnomalyList(insightMap);
        if (text.isBlank()) {
            DialogHelper.showInformation("", "当前附件未识别到异常项");
            return;
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
        DialogHelper.showInformation("", "异常清单已复制");
    }

    @FXML
    public void onExportAttachmentStructuredSummary() {
        if (pendingAttachments.isEmpty()) {
            DialogHelper.showInformation("", "暂无可导出的附件摘要");
            return;
        }
        Map<String, AttachmentInsight> insightMap = buildAttachmentInsightMap();
        String content = buildAttachmentStructuredSummaryExport(insightMap);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出结构化摘要");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Markdown", "*.md"),
                new FileChooser.ExtensionFilter("Text", "*.txt")
        );
        chooser.setInitialFileName(
                "attachment-summary-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md"
        );
        File file = chooser.showSaveDialog(messageInput.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            DialogHelper.showInformation("", "结构化摘要已导出");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to export attachment structured summary", e);
            DialogHelper.showInformation("", "导出失败，请稍后重试");
        }
    }

    private String buildAttachmentDetailText(Map<String, AttachmentInsight> insightMap, boolean onlyAnomaly) {
        if (pendingAttachments.isEmpty() || insightMap == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < pendingAttachments.size(); i++) {
            File file = pendingAttachments.get(i);
            if (file == null) {
                continue;
            }
            AttachmentInsight insight = insightMap.get(file.getAbsolutePath());
            if (onlyAnomaly && (insight == null || !insight.hasAnomaly())) {
                continue;
            }
            AttachmentContextBuilder.AttachmentItem item = attachmentStatus.get(file.getAbsolutePath());
            String status = item != null && item.status() != null && !item.status().isBlank()
                    ? item.status()
                    : "待解析";
            String detail = item != null && item.detail() != null && !item.detail().isBlank()
                    ? item.detail()
                    : "暂无详情";
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(shown + 1).append(". ").append(file.getName())
                    .append(" | ").append(status)
                    .append(" | ").append(detail)
                    .append("\n   - 异常: ").append(insight != null && insight.hasAnomaly() ? "有" : "无");
            if (insight != null && insight.structured() != null && !insight.structured().isBlank()) {
                sb.append("\n   - 结构化要点: ").append(insight.structured());
            }
            if (insight != null && insight.anomalyText() != null && !insight.anomalyText().isBlank()) {
                sb.append("\n   - 异常详情: ").append(insight.anomalyText());
            }
            shown++;
        }
        if (onlyAnomaly && shown == 0) {
            return "当前附件中暂无异常项，建议切换“查看全部附件”。";
        }
        return sb.toString();
    }

    private String buildAttachmentAnomalyList(Map<String, AttachmentInsight> insightMap) {
        if (insightMap == null || insightMap.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("异常附件清单\n");
        int idx = 1;
        for (File file : pendingAttachments) {
            if (file == null) {
                continue;
            }
            AttachmentInsight insight = insightMap.get(file.getAbsolutePath());
            if (insight == null || !insight.hasAnomaly()) {
                continue;
            }
            sb.append(idx++).append(". ").append(file.getName()).append("\n");
            if (insight.anomalyText() != null && !insight.anomalyText().isBlank()) {
                sb.append("异常详情: ").append(insight.anomalyText()).append("\n");
            }
            sb.append("\n");
        }
        if (idx == 1) {
            return "";
        }
        return sb.toString().trim();
    }

    private String buildAttachmentStructuredSummaryExport(Map<String, AttachmentInsight> insightMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 附件结构化摘要\n\n");
        sb.append("- 导出时间: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("\n");
        sb.append("- 附件数量: ").append(pendingAttachments.size()).append("\n\n");
        for (File file : pendingAttachments) {
            if (file == null) {
                continue;
            }
            AttachmentContextBuilder.AttachmentItem item = attachmentStatus.get(file.getAbsolutePath());
            AttachmentInsight insight = insightMap.get(file.getAbsolutePath());
            sb.append("## ").append(file.getName()).append("\n");
            sb.append("- 状态: ")
                    .append(item != null && item.status() != null && !item.status().isBlank() ? item.status() : "待解析")
                    .append("\n");
            sb.append("- 详情: ")
                    .append(item != null && item.detail() != null && !item.detail().isBlank() ? item.detail() : "暂无详情")
                    .append("\n");
            sb.append("- 是否异常: ").append(insight != null && insight.hasAnomaly() ? "是" : "否").append("\n");
            if (insight != null && insight.structured() != null && !insight.structured().isBlank()) {
                sb.append("- 结构化要点: ").append(insight.structured()).append("\n");
            }
            if (insight != null && insight.anomalyText() != null && !insight.anomalyText().isBlank()) {
                sb.append("- 异常详情: ").append(insight.anomalyText()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private Map<String, AttachmentInsight> buildAttachmentInsightMap() {
        Map<String, AttachmentInsight> result = new LinkedHashMap<>();
        for (File file : pendingAttachments) {
            if (file != null) {
                result.put(file.getAbsolutePath(), new AttachmentInsight(false, "", ""));
            }
        }
        if (pendingAttachments.isEmpty()) {
            return result;
        }
        String requestText;
        try {
            AttachmentContextBuilder.AttachmentPayload payload =
                    AttachmentContextBuilder.buildPayload("", pendingAttachments);
            requestText = payload.requestMessage();
        } catch (Exception e) {
            return result;
        }
        if (requestText == null || requestText.isBlank()) {
            return result;
        }
        for (int i = 0; i < pendingAttachments.size(); i++) {
            File file = pendingAttachments.get(i);
            if (file == null) {
                continue;
            }
            String marker = "附件" + (i + 1) + "：" + file.getName() + "\n";
            int start = requestText.indexOf(marker);
            if (start < 0) {
                continue;
            }
            int blockStart = start + marker.length();
            int end = requestText.indexOf("\n---\n附件", blockStart);
            if (end < 0) {
                end = requestText.indexOf("\n【批量附件总览】", blockStart);
            }
            if (end < 0) {
                end = requestText.length();
            }
            String block = requestText.substring(blockStart, end);
            String structured = extractSection(block, "结构化要点:", "异常提示:", "内容:");
            String anomaly = extractSection(block, "异常提示:", "内容:");
            boolean hasAnomaly = anomaly != null && !anomaly.isBlank();
            result.put(file.getAbsolutePath(), new AttachmentInsight(
                    hasAnomaly,
                    flattenMultiline(structured),
                    flattenMultiline(anomaly)
            ));
        }
        return result;
    }

    private String extractSection(String block, String startToken, String... endTokens) {
        if (block == null || block.isBlank() || startToken == null || startToken.isBlank()) {
            return "";
        }
        int start = block.indexOf(startToken);
        if (start < 0) {
            return "";
        }
        int from = start + startToken.length();
        int end = block.length();
        if (endTokens != null) {
            for (String endToken : endTokens) {
                if (endToken == null || endToken.isBlank()) {
                    continue;
                }
                int idx = block.indexOf(endToken, from);
                if (idx >= 0 && idx < end) {
                    end = idx;
                }
            }
        }
        return block.substring(from, end).trim();
    }

    private String flattenMultiline(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace('\r', '\n')
                .replaceAll("\\n+", " | ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    // ---- Accessors for testing ----

    Label getSkillNameLabel() { return skillNameLabel; }
    Label getSkillTypeBadge() { return skillTypeBadge; }
    Button getUsageGuideBtn() { return usageGuideBtn; }
    ScrollPane getChatScrollPane() { return chatScrollPane; }
    VBox getMessagesContainer() { return messagesContainer; }
    HBox getLoadingBox() { return loadingBox; }
    Label getErrorLabel() { return errorLabel; }
    TextArea getMessageInput() { return messageInput; }
    Button getSendBtn() { return sendBtn; }
    Skill getCurrentSkill() { return currentSkill; }
    String getSessionId() { return sessionId; }
    List<ChatMessage> getConversationHistory() { return conversationHistory; }
    boolean isSending() { return sending; }

    private record AttachmentInsight(boolean hasAnomaly, String structured, String anomalyText) {
    }

    @FXML
    public void onExportResult() {
        String latestOutput = resolveLatestAssistantOutput();
        if (latestOutput == null || latestOutput.isBlank()) {
            DialogHelper.showInformation("", "没有可导出的结果");
            return;
        }
        java.nio.file.Path generatedFile = resolveGeneratedFilePath(latestOutput);
        if (generatedFile != null) {
            exportGeneratedFile(generatedFile);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出最近输出");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Markdown", "*.md"),
                new FileChooser.ExtensionFilter("Text", "*.txt")
        );
        chooser.setInitialFileName(
                "latest-output-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md"
        );
        File file = chooser.showSaveDialog(messageInput.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), latestOutput);
            DialogHelper.showInformation("", "导出成功！");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to export result", e);
            DialogHelper.showInformation("", "导出失败，请重试");
        }
    }

    @FXML
    public void onClearHistory() {
        if (sessionId == null || conversationHistory.isEmpty()) {
            DialogHelper.showInformation("", "暂无聊天记录");
            return;
        }

        var result = DialogHelper.showConfirmation("", "确定要清空当前对话吗？");
        if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) {
            return;
        }

        // Clear from database
        if (chatService != null) {
            chatService.clearHistory(sessionId);
        }

        // Clear UI
        conversationHistory.clear();
        messagesContainer.getChildren().clear();
        lastAiContent = null;
        hideError();

        if (onHistoryClearedCallback != null) {
            onHistoryClearedCallback.run();
        }
    }

    private void beginStreamingBubble() {
        streamingBuffer = new StringBuilder();

        streamingLabel = new Label("");
        streamingLabel.setWrapText(true);
        streamingLabel.setTextOverrun(OverrunStyle.CLIP);
        streamingLabel.setMaxWidth(520);
        streamingLabel.setMinHeight(Region.USE_PREF_SIZE);
        streamingLabel.setPrefHeight(Region.USE_COMPUTED_SIZE);
        streamingLabel.getStyleClass().add("chat-bubble-ai");
        streamingLabel.setCursor(javafx.scene.Cursor.TEXT);

        streamingRow = new HBox();
        streamingRow.setPadding(new Insets(2, 0, 2, 0));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        streamingRow.getChildren().addAll(streamingLabel, spacer);
        streamingRow.setAlignment(Pos.CENTER_LEFT);
        messagesContainer.getChildren().add(streamingRow);
        ensureScrollToBottom();
    }

    private void appendStreaming(String delta) {
        if (stopRequested || streamingBuffer == null || streamingLabel == null || delta == null) {
            return;
        }
        streamingBuffer.append(delta);
        String full = streamingBuffer.toString();
        streamingLabel.setText(full);
        streamingRow.requestLayout();
        messagesContainer.requestLayout();
        ensureScrollToBottom();
    }

    private void removeStreamingBubble() {
        if (streamingRow != null) {
            messagesContainer.getChildren().remove(streamingRow);
        }
        streamingRow = null;
        streamingLabel = null;
        streamingBuffer = null;
    }

    @FXML
    public void onStopGenerating() {
        if (!sending) {
            return;
        }
        stopRequested = true;
        if (activeStreamingFuture != null) {
            activeStreamingFuture.cancel(true);
            activeStreamingFuture = null;
        }
        String partial = streamingBuffer != null ? streamingBuffer.toString().trim() : "";
        removeStreamingBubble();
        setLoading(false);
        if (!partial.isBlank()) {
            String stopped = partial + "\n\n（已停止生成）";
            ChatMessage aiMsg = new ChatMessage(
                    UUID.randomUUID().toString(),
                    sessionId,
                    currentSkill != null ? currentSkill.id() : "",
                    "assistant",
                    stopped,
                    System.currentTimeMillis()
            );
            conversationHistory.add(aiMsg);
            appendMessageBubble("assistant", stopped);
            lastAiContent = stopped;
            if (chatService != null) {
                chatService.saveMessage(aiMsg, currentUserId, currentSkill != null ? currentSkill.name() : "Skill");
            }
        }
    }

    private String resolveLatestAssistantOutput() {
        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            ChatMessage msg = conversationHistory.get(i);
            if (msg == null) {
                continue;
            }
            if ("assistant".equalsIgnoreCase(msg.role()) && msg.content() != null && !msg.content().isBlank()) {
                return msg.content();
            }
        }
        return (lastAiContent != null && !lastAiContent.isBlank()) ? lastAiContent : null;
    }

    private java.nio.file.Path resolveGeneratedFilePath(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String[] lines = content.split("\\R");
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.startsWith("文件路径:") || line.startsWith("文件路径：")
                    || line.toLowerCase().startsWith("file path:")) {
                String pathValue = line.substring(line.indexOf(':') + 1).trim();
                if (pathValue.isEmpty()) {
                    continue;
                }
                try {
                    java.nio.file.Path path = java.nio.file.Path.of(pathValue);
                    if (java.nio.file.Files.exists(path) && java.nio.file.Files.isRegularFile(path)) {
                        return path;
                    }
                } catch (Exception ignored) {
                    // ignore invalid path text
                }
            }
        }
        return null;
    }

    private void exportGeneratedFile(java.nio.file.Path sourcePath) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("导出最近输出文件");
            chooser.setInitialFileName(sourcePath.getFileName().toString());
            File target = chooser.showSaveDialog(messageInput.getScene().getWindow());
            if (target == null) {
                return;
            }
            java.nio.file.Files.copy(
                    sourcePath,
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            DialogHelper.showInformation("", "导出成功！");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to export generated file", e);
            DialogHelper.showInformation("", "导出失败，请重试");
        }
    }

    private void ensureScrollToBottom() {
        if (chatScrollPane == null) {
            return;
        }
        // Repeated push to bottom avoids losing follow when layout updates rapidly during streaming.
        chatScrollPane.setVvalue(1.0);
        Platform.runLater(() -> {
            if (chatScrollPane != null) {
                chatScrollPane.layout();
                chatScrollPane.setVvalue(1.0);
                Platform.runLater(() -> {
                    if (chatScrollPane != null) {
                        chatScrollPane.setVvalue(1.0);
                    }
                });
            }
        });
    }

    private void clearBubbleTextAreaBackground(TextArea textArea) {
        if (textArea == null) {
            return;
        }
        Runnable apply = () -> {
            textArea.lookupAll(".content").forEach(n ->
                    n.setStyle("-fx-background-color: transparent; -fx-background-insets: 0;"));
            textArea.lookupAll(".scroll-pane").forEach(n ->
                    n.setStyle("-fx-background-color: transparent; -fx-background-insets: 0;"));
            textArea.lookupAll(".scroll-pane .viewport").forEach(n ->
                    n.setStyle("-fx-background-color: transparent; -fx-background-insets: 0;"));
        };
        textArea.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(apply);
            }
        });
        Platform.runLater(apply);
    }

    private void installBubbleContextMenu(TextArea textArea) {
        if (textArea == null) {
            return;
        }
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("chat-copy-menu");

        MenuItem copyItem = new MenuItem("复制");
        copyItem.getStyleClass().add("chat-copy-menu-item");
        copyItem.setOnAction(e -> {
            String selected = textArea.getSelectedText();
            String toCopy = (selected != null && !selected.isBlank()) ? selected : textArea.getText();
            if (toCopy != null && !toCopy.isEmpty()) {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(toCopy);
                Clipboard.getSystemClipboard().setContent(cc);
            }
        });
        menu.getItems().setAll(copyItem);
        textArea.setContextMenu(menu);
    }

    private String normalizeContextPolicy(Skill skill) {
        if (skill == null || skill.contextPolicy() == null || skill.contextPolicy().isBlank()) {
            return "standard";
        }
        String value = skill.contextPolicy().trim().toLowerCase();
        return switch (value) {
            case "minimal", "advanced", "standard" -> value;
            case "default" -> "standard";
            default -> "standard";
        };
    }
}
