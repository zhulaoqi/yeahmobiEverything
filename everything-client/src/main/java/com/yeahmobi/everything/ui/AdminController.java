package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.admin.AdminService;
import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.admin.SkillIntegrationResult;
import com.yeahmobi.everything.feedback.Feedback;
import com.yeahmobi.everything.personalskill.PersonalSkill;
import com.yeahmobi.everything.personalskill.PersonalSkillResult;
import com.yeahmobi.everything.personalskill.PersonalSkillService;
import com.yeahmobi.everything.personalskill.PersonalSkillStatus;
import com.yeahmobi.everything.skill.SkillImportReport;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the admin management page (admin.fxml).
 * <p>
 * Provides:
 * <ul>
 *   <li>Skill management list (name, status, type, kind, description)</li>
 *   <li>Command-line input area (TextArea + execute button) for integrating new Skills</li>
 *   <li>Skill enable/disable toggle</li>
 *   <li>Feedback list view (time descending, mark as processed)</li>
 * </ul>
 * </p>
 */
public class AdminController {

    private static final Logger LOGGER = Logger.getLogger(AdminController.class.getName());
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DEFAULT_SKILL_REPO_URL = "https://github.com/anthropics/skills";
    private static final String SKILL_REPO_CACHE_DIR =
            System.getProperty("user.home") + File.separator + ".yeahmobi-everything"
                    + File.separator + "skill-repos";

    // ---- FXML bindings ----

    @FXML private Button createSkillButton;
    @FXML private Button manageKnowledgeButton;
    @FXML private TextField skillRepoInput;
    @FXML private Button importAnthropicButton;
    @FXML private TabPane adminTabPane;
    @FXML private TextArea commandInput;
    @FXML private Button executeCommandButton;
    @FXML private Label commandResultLabel;
    @FXML private VBox skillListContainer;
    @FXML private VBox feedbackListContainer;
    @FXML private VBox personalSkillListContainer;
    @FXML private TextField promoteUserInput;
    @FXML private Button promoteUserButton;
    @FXML private Label promoteUserResultLabel;

    // ---- Dependencies ----

    private AdminService adminService;
    private PersonalSkillService personalSkillService;

    /** Callback for navigating to the Skill creation wizard. */
    private Runnable onCreateSkillCallback;

    /** Callback for navigating to the knowledge management page. */
    private Runnable onManageKnowledgeCallback;
    /** Callback for opening a Skill in chat (by skillId). */
    private java.util.function.Consumer<String> onUseSkillCallback;

    // ---- Internal state ----

    private List<SkillAdmin> currentSkills = new ArrayList<>();
    private List<Feedback> currentFeedbacks = new ArrayList<>();
    private List<PersonalSkill> pendingPersonalSkills = new ArrayList<>();

    // ---- Dependency injection ----

    public void setAdminService(AdminService adminService) {
        this.adminService = adminService;
    }

    public void setPersonalSkillService(PersonalSkillService personalSkillService) {
        this.personalSkillService = personalSkillService;
    }

    public void setOnCreateSkillCallback(Runnable callback) {
        this.onCreateSkillCallback = callback;
    }

    public void setOnManageKnowledgeCallback(Runnable callback) {
        this.onManageKnowledgeCallback = callback;
    }

    public void setOnUseSkillCallback(java.util.function.Consumer<String> callback) {
        this.onUseSkillCallback = callback;
    }

    // ---- Initialization ----

    @FXML
    public void initialize() {
        // Initial state: hide command result label
        if (commandResultLabel != null) {
            commandResultLabel.setManaged(false);
            commandResultLabel.setVisible(false);
        }
        if (promoteUserResultLabel != null) {
            promoteUserResultLabel.setManaged(false);
            promoteUserResultLabel.setVisible(false);
        }
    }

    /**
     * Loads all Skills and Feedbacks from the AdminService and populates the UI.
     * Should be called after setting the AdminService dependency.
     */
    public void loadData() {
        loadSkillList();
        loadFeedbackList();
        loadPersonalSkillList();
    }

    // ---- Navigation handlers ----

    @FXML
    public void onCreateSkill() {
        if (onCreateSkillCallback != null) {
            onCreateSkillCallback.run();
        }
    }

    @FXML
    public void onManageKnowledge() {
        if (onManageKnowledgeCallback != null) {
            onManageKnowledgeCallback.run();
        }
    }

    @FXML
    public void onImportAnthropicSkills() {
        if (adminService == null) {
            showCommandResult(false, "管理服务不可用");
            return;
        }
        String input = skillRepoInput != null ? skillRepoInput.getText() : null;
        String repoInput = (input == null || input.isBlank())
                ? DEFAULT_SKILL_REPO_URL
                : input.trim();
        importAnthropicButton.setDisable(true);
        showCommandResult(true, "正在导入技能，请稍候...");
        
        // Run import in background thread to avoid UI freeze
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                String localPath = resolveRepoPath(repoInput);
                if (!ensureRepoAvailable(repoInput, localPath)) {
                    return new ImportResult(false, "无法获取 Skills 仓库（请检查地址、git 或网络）", 0);
                }
                SkillImportReport report = adminService.importSkillsFromPathDetailed(localPath);
                if (!report.success()) {
                    return new ImportResult(false, "导入失败: " + report.reason(), 0);
                }
                int count = report.changedCount();
                return new ImportResult(true, buildImportMessage(report), count);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to import Anthropic skills", e);
                return new ImportResult(false, "导入失败: " + e.getMessage(), 0);
            }
        }).thenAccept(result -> javafx.application.Platform.runLater(() -> {
            importAnthropicButton.setDisable(false);
            showCommandResult(result.success, result.message);
            if (result.success && result.count > 0) {
                loadSkillList();
            }
        }));
    }
    
    private static class ImportResult {
        final boolean success;
        final String message;
        final int count;
        ImportResult(boolean success, String message, int count) {
            this.success = success;
            this.message = message;
            this.count = count;
        }
    }

    private String buildImportMessage(SkillImportReport report) {
        if (report.scanned() == 0) {
            return "未导入任何技能：仓库中未发现 SKILL.md 文件，请检查仓库结构（应为 skills/*/SKILL.md）";
        }
        if (report.changedCount() == 0) {
            String base = "本次未导入新技能：扫描 " + report.scanned()
                    + " 个，全部已存在且无可更新字段（跳过 " + report.skipped()
                    + " 个）。如需生效，请检查仓库是否有新增/变更技能。";
            if (report.hasLocalizationFailures()) {
                return base + " 另外，中文增强失败 " + report.localizationFailed()
                        + " 个（通常是网络超时），可稍后重试导入或执行批量补全。";
            }
            return base;
        }
        StringBuilder message = new StringBuilder();
        message.append("导入完成：扫描 ").append(report.scanned()).append(" 个，新增 ")
                .append(report.imported()).append(" 个，更新 ")
                .append(report.repaired()).append(" 个，跳过 ")
                .append(report.skipped()).append(" 个");
        if (report.failed() > 0) {
            message.append("，失败 ").append(report.failed()).append(" 个（可重试查看日志）");
        }
        if (report.hasLocalizationFailures()) {
            message.append("。中文增强失败 ").append(report.localizationFailed())
                    .append(" 个（通常是网络超时），可稍后重试导入或执行批量补全。");
        }
        return message.toString();
    }

    private String resolveRepoPath(String repoInput) {
        if (!isUrl(repoInput)) {
            return repoInput;
        }
        String safeName = repoInput.replaceAll("[^a-zA-Z0-9._-]", "_");
        return SKILL_REPO_CACHE_DIR + File.separator + safeName;
    }

    private boolean ensureRepoAvailable(String repoInput, String localPath) {
        File repoDir = new File(localPath);
        File skillsDir = new File(repoDir, "skills");
        if (!isUrl(repoInput)) {
            return repoDir.exists();
        }
        if (repoDir.exists() && skillsDir.exists()) {
            return pullRepo(localPath);
        }
        File parent = repoDir.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        return cloneRepo(repoInput, localPath);
    }

    private boolean cloneRepo(String repoUrl, String path) {
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "git", "clone", "--depth", "1", repoUrl, path);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // consume output
                }
            }
            int code = process.waitFor();
            return code == 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to clone Anthropic skills repo", e);
            return false;
        }
    }

    private boolean pullRepo(String path) {
        try {
            ProcessBuilder builder = new ProcessBuilder("git", "-C", path, "pull", "--ff-only");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // consume output
                }
            }
            int code = process.waitFor();
            return code == 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to pull skills repo", e);
            return false;
        }
    }

    private boolean isUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    // ---- Command-line integration ----

    @FXML
    public void onExecuteCommand() {
        String command = commandInput.getText();
        if (command == null || command.isBlank()) {
            showCommandResult(false, "请输入命令");
            return;
        }

        if (adminService == null) {
            showCommandResult(false, "管理服务不可用");
            return;
        }

        executeCommandButton.setDisable(true);
        try {
            SkillIntegrationResult result = adminService.integrateSkill(command.trim());
            showCommandResult(result.success(), result.message());
            if (result.success()) {
                commandInput.clear();
                loadSkillList(); // Refresh the skill list
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to execute command", e);
            showCommandResult(false, "命令执行失败: " + e.getMessage());
        } finally {
            executeCommandButton.setDisable(false);
        }
    }

    @FXML
    public void onPromoteUser() {
        if (adminService == null) {
            showPromoteResult(false, "管理服务不可用");
            return;
        }
        String value = promoteUserInput != null ? promoteUserInput.getText() : null;
        String message = adminService.promoteUserToAdmin(value);
        boolean success = message != null && message.contains("已设置");
        showPromoteResult(success, message != null ? message : "设置失败");
        if (success && promoteUserInput != null) {
            promoteUserInput.clear();
        }
    }

    // ---- Skill list management ----

    /**
     * Loads all Skills from the AdminService and renders them in the skill list container.
     */
    public void loadSkillList() {
        if (adminService == null) {
            return;
        }

        try {
            currentSkills = adminService.getAllSkills();
            renderSkillList();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load skill list", e);
        }
    }

    private void renderSkillList() {
        skillListContainer.getChildren().clear();

        if (currentSkills.isEmpty()) {
            Label emptyLabel = new Label("暂无已集成的 Skill");
            emptyLabel.getStyleClass().add("section-subtitle");
            skillListContainer.getChildren().add(emptyLabel);
            return;
        }

        for (SkillAdmin skill : currentSkills) {
            skillListContainer.getChildren().add(createSkillRow(skill));
        }
    }

    /**
     * Creates a single Skill row for the management list.
     * Shows: name, status (enabled/disabled toggle), type, kind, description.
     */
    VBox createSkillRow(SkillAdmin skill) {
        VBox card = new VBox(6);
        card.getStyleClass().add("skill-card");
        card.setStyle("-fx-padding: 12; -fx-border-color: #D0D7DE; -fx-border-radius: 6; -fx-background-radius: 6;");

        // Top row: name + status toggle + type badges
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(skill.name());
        nameLabel.getStyleClass().add("card-title");
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // Status toggle button
        Button toggleButton = new Button(skill.enabled() ? "已启用" : "已禁用");
        toggleButton.getStyleClass().add(skill.enabled() ? "btn-primary" : "btn-secondary");
        toggleButton.setStyle("-fx-padding: 2 10 2 10; -fx-font-size: 11px;");
        toggleButton.setOnAction(e -> handleToggleSkillStatus(skill, toggleButton));

        // Type badge (GENERAL / INTERNAL)
        Label typeLabel = new Label(skill.type() != null ? skill.type().name() : "GENERAL");
        typeLabel.setStyle("-fx-padding: 2 8 2 8; -fx-font-size: 11px; -fx-background-color: #DDF4FF; -fx-background-radius: 4; -fx-text-fill: #0969DA;");

        // Kind badge (PROMPT_ONLY / KNOWLEDGE_RAG)
        Label kindLabel = new Label(skill.kind() != null ? skill.kind().name() : "PROMPT_ONLY");
        kindLabel.setStyle("-fx-padding: 2 8 2 8; -fx-font-size: 11px; -fx-background-color: #DAFBE1; -fx-background-radius: 4; -fx-text-fill: #1F883D;");

        // Quality tier badge (basic / verified)
        String tier = (skill.qualityTier() != null && !skill.qualityTier().isBlank()) ? skill.qualityTier().trim() : "basic";
        Label tierLabel = new Label("verified".equalsIgnoreCase(tier) ? "已验证" : "基础");
        tierLabel.setStyle("verified".equalsIgnoreCase(tier)
                ? "-fx-padding: 2 8 2 8; -fx-font-size: 11px; -fx-background-color: #FFF8C5; -fx-background-radius: 4; -fx-text-fill: #9A6700;"
                : "-fx-padding: 2 8 2 8; -fx-font-size: 11px; -fx-background-color: #F3F4F6; -fx-background-radius: 4; -fx-text-fill: #6B7280;");

        Button editDisplayBtn = new Button("编辑展示");
        editDisplayBtn.getStyleClass().add("btn-secondary");
        editDisplayBtn.setStyle("-fx-padding: 2 10 2 10; -fx-font-size: 11px;");
        editDisplayBtn.setOnAction(e -> openEditDisplayDialog(skill));

        Button useBtn = new Button("使用");
        useBtn.getStyleClass().add("btn-primary");
        useBtn.setStyle("-fx-padding: 2 10 2 10; -fx-font-size: 11px;");
        useBtn.setOnAction(e -> {
            if (onUseSkillCallback != null && skill != null && skill.id() != null && !skill.id().isBlank()) {
                onUseSkillCallback.accept(skill.id());
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topRow.getChildren().addAll(nameLabel, spacer, tierLabel, typeLabel, kindLabel, useBtn, editDisplayBtn, toggleButton);

        // Description row
        Label descLabel = new Label(skill.description() != null ? skill.description() : "");
        descLabel.getStyleClass().add("card-description");
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(Double.MAX_VALUE);
        descLabel.setStyle("-fx-font-size: 13px;");

        card.getChildren().addAll(topRow, descLabel);
        return card;
    }

    private void openEditDisplayDialog(SkillAdmin skill) {
        if (adminService == null || skill == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("编辑展示字段");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox box = new VBox(10);
        box.setPrefWidth(640);

        Label tip = new Label("i18n_json 仅用于展示（如 zh-CN 的中文名/场景/示例）。quality_tier 用于标记是否已校验。");
        tip.getStyleClass().add("section-subtitle");
        tip.setWrapText(true);

        HBox tierRow = new HBox(8);
        tierRow.setAlignment(Pos.CENTER_LEFT);
        Label tierLabel = new Label("quality_tier:");
        ChoiceBox<String> tierChoice = new ChoiceBox<>();
        tierChoice.getItems().addAll("basic", "verified");
        String currentTier = (skill.qualityTier() != null && !skill.qualityTier().isBlank()) ? skill.qualityTier().trim() : "basic";
        tierChoice.setValue("verified".equalsIgnoreCase(currentTier) ? "verified" : "basic");
        tierRow.getChildren().addAll(tierLabel, tierChoice);

        Label jsonLabel = new Label("i18n_json:");
        TextArea jsonArea = new TextArea(skill.i18nJson() != null ? skill.i18nJson() : "");
        jsonArea.setWrapText(true);
        jsonArea.setPrefRowCount(14);

        box.getChildren().addAll(tip, tierRow, jsonLabel, jsonArea);
        dialog.getDialogPane().setContent(box);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) {
                return;
            }
            String json = jsonArea.getText();
            String tier = tierChoice.getValue();
            SkillIntegrationResult result = adminService.updateSkillDisplay(skill.id(), json, tier);
            DialogHelper.showInformation("", result.message());
            if (result.success()) {
                loadSkillList();
            }
        });
    }

    /**
     * Handles the enable/disable toggle for a Skill.
     */
    private void handleToggleSkillStatus(SkillAdmin skill, Button toggleButton) {
        if (adminService == null) {
            return;
        }

        boolean newStatus = !skill.enabled();
        try {
            adminService.toggleSkillStatus(skill.id(), newStatus);
            // Refresh the skill list to reflect the change
            loadSkillList();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to toggle skill status", e);
        }
    }

    // ---- Feedback list management ----

    /**
     * Loads all feedbacks from the AdminService and renders them in the feedback list container.
     * Feedbacks are displayed in time-descending order (as returned by AdminService).
     */
    public void loadFeedbackList() {
        if (adminService == null) {
            return;
        }

        try {
            currentFeedbacks = adminService.getAllFeedbacks();
            renderFeedbackList();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load feedback list", e);
        }
    }

    public void loadPersonalSkillList() {
        if (personalSkillService == null) {
            return;
        }
        try {
            pendingPersonalSkills = personalSkillService.listPending();
            renderPersonalSkillList();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load personal skill list", e);
        }
    }

    private void renderPersonalSkillList() {
        personalSkillListContainer.getChildren().clear();
        if (pendingPersonalSkills.isEmpty()) {
            Label emptyLabel = new Label("暂无待审核的个人 Skill");
            emptyLabel.getStyleClass().add("section-subtitle");
            personalSkillListContainer.getChildren().add(emptyLabel);
            return;
        }
        for (PersonalSkill skill : pendingPersonalSkills) {
            personalSkillListContainer.getChildren().add(createPersonalSkillRow(skill));
        }
    }

    private VBox createPersonalSkillRow(PersonalSkill skill) {
        VBox card = new VBox(6);
        card.getStyleClass().add("skill-card");
        card.setStyle("-fx-padding: 12; -fx-border-color: #D0D7DE; -fx-border-radius: 6; -fx-background-radius: 6;");

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(skill.name());
        nameLabel.getStyleClass().add("card-title");

        Label statusBadge = new Label("审核中");
        statusBadge.getStyleClass().add("skill-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topRow.getChildren().addAll(nameLabel, statusBadge, spacer);

        Label meta = new Label("分类: " + skill.category() + " · 用户ID: " + skill.userId());
        meta.getStyleClass().add("section-subtitle");

        Label desc = new Label(skill.description());
        desc.getStyleClass().add("card-description");
        desc.setWrapText(true);

        TextField noteField = new TextField();
        noteField.setPromptText("审核备注（可选）");
        noteField.getStyleClass().add("text-field");

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button approveBtn = new Button("通过");
        approveBtn.getStyleClass().add("btn-primary");
        approveBtn.setOnAction(e -> handleReviewPersonalSkill(skill, PersonalSkillStatus.APPROVED, noteField.getText()));

        Button rejectBtn = new Button("驳回");
        rejectBtn.getStyleClass().add("btn-secondary");
        rejectBtn.setOnAction(e -> handleReviewPersonalSkill(skill, PersonalSkillStatus.REJECTED, noteField.getText()));

        actions.getChildren().addAll(rejectBtn, approveBtn);

        card.getChildren().addAll(topRow, meta, desc, noteField, actions);
        return card;
    }

    private void handleReviewPersonalSkill(PersonalSkill skill, PersonalSkillStatus status, String note) {
        if (personalSkillService == null) {
            return;
        }
        PersonalSkillResult result = personalSkillService.reviewSkill(skill.id(), status, note);
        if (result.success()) {
            loadPersonalSkillList();
            if (status == PersonalSkillStatus.APPROVED) {
                loadSkillList();
            }
        }
    }

    private void renderFeedbackList() {
        feedbackListContainer.getChildren().clear();

        if (currentFeedbacks.isEmpty()) {
            Label emptyLabel = new Label("暂无用户反馈");
            emptyLabel.getStyleClass().add("section-subtitle");
            feedbackListContainer.getChildren().add(emptyLabel);
            return;
        }

        for (Feedback feedback : currentFeedbacks) {
            feedbackListContainer.getChildren().add(createFeedbackRow(feedback));
        }
    }

    /**
     * Creates a single feedback row for the feedback list.
     * Shows: username, content, time, status, and a "mark as processed" button.
     */
    VBox createFeedbackRow(Feedback feedback) {
        VBox card = new VBox(6);
        card.setStyle("-fx-padding: 12; -fx-border-color: #D0D7DE; -fx-border-radius: 6; -fx-background-radius: 6;");

        // Top row: username + time + status
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label userLabel = new Label("👤 " + (feedback.username() != null ? feedback.username() : "未知用户"));
        userLabel.getStyleClass().add("card-title");
        userLabel.setStyle("-fx-font-size: 13px;");

        Label timeLabel = new Label(formatTimestamp(feedback.timestamp()));
        timeLabel.getStyleClass().add("section-subtitle");
        timeLabel.setStyle("-fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        boolean isProcessed = "processed".equals(feedback.status());

        Label statusLabel = new Label(isProcessed ? "✅ 已处理" : "⏳ 待处理");
        statusLabel.setStyle(isProcessed
                ? "-fx-padding: 2 8 2 8; -fx-font-size: 11px; -fx-background-color: #DAFBE1; -fx-background-radius: 4; -fx-text-fill: #1F883D;"
                : "-fx-padding: 2 8 2 8; -fx-font-size: 11px; -fx-background-color: #FFF8C5; -fx-background-radius: 4; -fx-text-fill: #9A6700;");

        topRow.getChildren().addAll(userLabel, timeLabel, spacer, statusLabel);

        // Content row
        Label contentLabel = new Label(feedback.content() != null ? feedback.content() : "");
        contentLabel.getStyleClass().add("card-description");
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(Double.MAX_VALUE);
        contentLabel.setStyle("-fx-font-size: 13px;");

        card.getChildren().addAll(topRow, contentLabel);

        // Mark as processed button (only for pending feedbacks)
        if (!isProcessed) {
            HBox actionRow = new HBox(8);
            actionRow.setAlignment(Pos.CENTER_RIGHT);

            Button markProcessedButton = new Button("标记已处理");
            markProcessedButton.getStyleClass().add("btn-secondary");
            markProcessedButton.setStyle("-fx-padding: 4 12 4 12; -fx-font-size: 12px;");
            markProcessedButton.setOnAction(e -> handleMarkFeedbackProcessed(feedback));

            actionRow.getChildren().add(markProcessedButton);
            card.getChildren().add(actionRow);
        }

        return card;
    }

    /**
     * Handles marking a feedback as processed.
     */
    private void handleMarkFeedbackProcessed(Feedback feedback) {
        if (adminService == null) {
            return;
        }

        try {
            adminService.markFeedbackProcessed(feedback.id());
            // Refresh the feedback list to reflect the change
            loadFeedbackList();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to mark feedback as processed", e);
        }
    }

    // ---- Utility methods ----

    private void showCommandResult(boolean success, String message) {
        commandResultLabel.setText(message);
        commandResultLabel.setManaged(true);
        commandResultLabel.setVisible(true);
        if (success) {
            commandResultLabel.getStyleClass().removeAll("login-error-label");
            commandResultLabel.getStyleClass().add("section-subtitle");
            commandResultLabel.setStyle("-fx-text-fill: #1F883D;");
        } else {
            commandResultLabel.getStyleClass().removeAll("section-subtitle");
            commandResultLabel.getStyleClass().add("login-error-label");
            commandResultLabel.setStyle("");
        }
    }

    private void showPromoteResult(boolean success, String message) {
        promoteUserResultLabel.setText(message);
        promoteUserResultLabel.setManaged(true);
        promoteUserResultLabel.setVisible(true);
        if (success) {
            promoteUserResultLabel.getStyleClass().removeAll("login-error-label");
            promoteUserResultLabel.getStyleClass().add("section-subtitle");
            promoteUserResultLabel.setStyle("-fx-text-fill: #1F883D;");
        } else {
            promoteUserResultLabel.getStyleClass().removeAll("section-subtitle");
            promoteUserResultLabel.getStyleClass().add("login-error-label");
            promoteUserResultLabel.setStyle("");
        }
    }

    static String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0) {
            return "";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return dateTime.format(TIME_FORMAT);
    }

    // ---- Package-private accessors for testing ----

    Button getCreateSkillButton() { return createSkillButton; }
    Button getManageKnowledgeButton() { return manageKnowledgeButton; }
    TabPane getAdminTabPane() { return adminTabPane; }
    TextArea getCommandInput() { return commandInput; }
    Button getExecuteCommandButton() { return executeCommandButton; }
    Label getCommandResultLabel() { return commandResultLabel; }
    VBox getSkillListContainer() { return skillListContainer; }
    VBox getFeedbackListContainer() { return feedbackListContainer; }
    List<SkillAdmin> getCurrentSkills() { return currentSkills; }
    List<Feedback> getCurrentFeedbacks() { return currentFeedbacks; }
}
