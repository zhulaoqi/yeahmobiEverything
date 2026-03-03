package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.admin.AdminService;
import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.knowledge.FileProcessingException;
import com.yeahmobi.everything.knowledge.KnowledgeBaseService;
import com.yeahmobi.everything.knowledge.KnowledgeFile;
import com.yeahmobi.everything.repository.mysql.SkillKnowledgeBindingRepository;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the knowledge base management page (knowledge.fxml).
 * <p>
 * Provides:
 * <ul>
 *   <li>Knowledge file list (file name, type, size, upload time, associated Skills)</li>
 *   <li>File upload (format validation: PDF, Markdown, TXT only), update, and delete</li>
 *   <li>Manual input knowledge content editing</li>
 *   <li>Error prompts for file extraction failures with manual input fallback</li>
 * </ul>
 * </p>
 */
public class KnowledgeController {

    private static final Logger LOGGER = Logger.getLogger(KnowledgeController.class.getName());
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ---- FXML bindings ----

    @FXML private Button backButton;
    @FXML private Label pageTitle;
    @FXML private Button uploadButton;
    @FXML private Button manualInputButton;
    @FXML private StackPane contentArea;

    // File list view
    @FXML private VBox fileListView;
    @FXML private Label fileCountLabel;
    @FXML private Label statusLabel;
    @FXML private VBox fileListContainer;

    // Edit view (manual input / edit)
    @FXML private ScrollPane editView;
    @FXML private Label editTitle;
    @FXML private Label editSubtitle;
    @FXML private Label editTitleLabel;
    @FXML private TextField editTitleField;
    @FXML private Label editContentLabel;
    @FXML private TextArea editContentField;
    @FXML private Label editValidationError;
    @FXML private Button editCancelButton;
    @FXML private Button editSaveButton;

    // ---- Dependencies ----

    private KnowledgeBaseService knowledgeBaseService;
    private SkillKnowledgeBindingRepository bindingRepository;
    private AdminService adminService;

    /** Callback for navigating back to the admin page. */
    private Runnable onBackCallback;

    // ---- Internal state ----

    private List<KnowledgeFile> currentFiles = new ArrayList<>();

    /** Skill name cache: skillId -> skillName */
    private Map<String, String> skillNameCache = new HashMap<>();

    /** The file ID being edited (null for new manual input). */
    private String editingFileId;

    // ---- Dependency injection ----

    public void setKnowledgeBaseService(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public void setBindingRepository(SkillKnowledgeBindingRepository bindingRepository) {
        this.bindingRepository = bindingRepository;
    }

    public void setAdminService(AdminService adminService) {
        this.adminService = adminService;
    }

    public void setOnBackCallback(Runnable callback) {
        this.onBackCallback = callback;
    }

    // ---- Initialization ----

    @FXML
    public void initialize() {
        // Initial state: hide status label and validation error
        if (statusLabel != null) {
            statusLabel.setManaged(false);
            statusLabel.setVisible(false);
        }
        if (editValidationError != null) {
            editValidationError.setManaged(false);
            editValidationError.setVisible(false);
        }
    }

    /**
     * Loads all knowledge files and populates the UI.
     * Should be called after setting dependencies.
     */
    public void loadData() {
        loadSkillNameCache();
        loadFileList();
    }

    // ---- Navigation handlers ----

    @FXML
    public void onBack() {
        if (onBackCallback != null) {
            onBackCallback.run();
        }
    }

    // ---- File upload ----

    @FXML
    public void onUploadFile() {
        if (knowledgeBaseService == null) {
            showStatus(false, "知识库服务不可用");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择知识库文件");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("支持的文件", "*.pdf", "*.md", "*.txt"),
                new FileChooser.ExtensionFilter("PDF 文件", "*.pdf"),
                new FileChooser.ExtensionFilter("Markdown 文件", "*.md"),
                new FileChooser.ExtensionFilter("文本文件", "*.txt"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        List<File> files = null;
        if (uploadButton != null && uploadButton.getScene() != null
                && uploadButton.getScene().getWindow() != null) {
            files = fileChooser.showOpenMultipleDialog(uploadButton.getScene().getWindow());
        }

        if (files != null && !files.isEmpty()) {
            uploadFiles(files);
        }
    }

    /**
     * Uploads the given files asynchronously, showing progress and handling errors.
     */
    void uploadFiles(List<File> files) {
        uploadButton.setDisable(true);
        showStatus(true, "正在上传...");

        CompletableFuture.runAsync(() -> {
            List<String> errors = new ArrayList<>();
            int successCount = 0;

            for (File file : files) {
                // Validate format
                if (!knowledgeBaseService.isSupportedFormat(file.getName())) {
                    errors.add(file.getName() + ": 不支持的文件格式（仅支持 PDF、Markdown、TXT）");
                    continue;
                }

                try {
                    knowledgeBaseService.uploadFile(file);
                    successCount++;
                } catch (FileProcessingException e) {
                    LOGGER.log(Level.WARNING, "Failed to upload file: " + file.getName(), e);
                    errors.add(file.getName() + ": " + e.getMessage());
                }
            }

            final int finalSuccessCount = successCount;
            final List<String> finalErrors = errors;

            Platform.runLater(() -> {
                uploadButton.setDisable(false);
                loadFileList();

                if (finalErrors.isEmpty()) {
                    showStatus(true, "成功上传 " + finalSuccessCount + " 个文件");
                } else if (finalSuccessCount > 0) {
                    showStatus(false, "上传 " + finalSuccessCount + " 个成功，"
                            + finalErrors.size() + " 个失败");
                    showExtractionFailureDialog(finalErrors);
                } else {
                    showStatus(false, "上传失败");
                    showExtractionFailureDialog(finalErrors);
                }
            });
        });
    }

    // ---- Manual input ----

    @FXML
    public void onManualInput() {
        editingFileId = null;
        editTitle.setText("手动输入知识内容");
        editSubtitle.setText("直接输入知识内容（如 FAQ、流程手册等）。");
        editTitleField.setText("");
        editTitleField.setDisable(false);
        editContentField.setText("");
        hideError(editValidationError);
        showEditView();
    }

    // ---- Edit view actions ----

    @FXML
    public void onEditCancel() {
        showFileListView();
    }

    @FXML
    public void onEditSave() {
        hideError(editValidationError);

        String title = editTitleField.getText();
        String content = editContentField.getText();

        // Validate required fields
        List<String> missingFields = new ArrayList<>();
        if (title == null || title.isBlank()) {
            missingFields.add("标题");
        }
        if (content == null || content.isBlank()) {
            missingFields.add("内容");
        }

        if (!missingFields.isEmpty()) {
            showError(editValidationError, "请填写必填字段：" + String.join("、", missingFields));
            return;
        }

        if (knowledgeBaseService == null) {
            showError(editValidationError, "知识库服务不可用");
            return;
        }

        editSaveButton.setDisable(true);

        CompletableFuture.runAsync(() -> {
            try {
                if (editingFileId != null) {
                    // Update existing manual content
                    knowledgeBaseService.updateManualContent(editingFileId, content.trim());
                } else {
                    // Create new manual input entry
                    knowledgeBaseService.createFromManualInput(title.trim(), content.trim());
                }

                Platform.runLater(() -> {
                    editSaveButton.setDisable(false);
                    showFileListView();
                    loadFileList();
                    showStatus(true, editingFileId != null ? "更新成功" : "创建成功");
                });
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to save knowledge content", e);
                Platform.runLater(() -> {
                    editSaveButton.setDisable(false);
                    showError(editValidationError, "保存失败: " + e.getMessage());
                });
            }
        });
    }

    // ---- File list management ----

    /**
     * Loads all knowledge files from the service and renders them.
     */
    public void loadFileList() {
        if (knowledgeBaseService == null) {
            return;
        }

        try {
            currentFiles = knowledgeBaseService.getAllFiles();
            renderFileList();
            updateFileCount();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load knowledge file list", e);
        }
    }

    private void renderFileList() {
        fileListContainer.getChildren().clear();

        if (currentFiles.isEmpty()) {
            Label emptyLabel = new Label("暂无知识库文件，点击「上传文件」或「手动输入」添加知识内容。");
            emptyLabel.getStyleClass().add("section-subtitle");
            emptyLabel.setWrapText(true);
            fileListContainer.getChildren().add(emptyLabel);
            return;
        }

        for (KnowledgeFile file : currentFiles) {
            fileListContainer.getChildren().add(createFileRow(file));
        }
    }

    /**
     * Creates a single file row for the knowledge file list.
     * Shows: file name, type, size, upload time, associated Skills, and action buttons.
     */
    VBox createFileRow(KnowledgeFile file) {
        VBox card = new VBox(6);
        card.setStyle("-fx-padding: 12; -fx-border-color: #D0D7DE; -fx-border-radius: 6; -fx-background-radius: 6;");

        // Top row: file icon + name + type badge + source badge
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        String icon = getFileIcon(file.fileType());
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 16px;");

        Label nameLabel = new Label(file.fileName());
        nameLabel.getStyleClass().add("card-title");
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // File type badge
        Label typeLabel = new Label(file.fileType() != null ? file.fileType().toUpperCase() : "");
        typeLabel.setStyle("-fx-padding: 2 8 2 8; -fx-font-size: 11px; -fx-background-color: #DDF4FF; "
                + "-fx-background-radius: 4; -fx-text-fill: #0969DA;");

        // Source type badge
        boolean isManual = "manual".equals(file.sourceType());
        Label sourceLabel = new Label(isManual ? "手动输入" : "文件上传");
        sourceLabel.setStyle(isManual
                ? "-fx-padding: 2 8 2 8; -fx-font-size: 11px; -fx-background-color: #FFF8C5; "
                  + "-fx-background-radius: 4; -fx-text-fill: #9A6700;"
                : "-fx-padding: 2 8 2 8; -fx-font-size: 11px; -fx-background-color: #DAFBE1; "
                  + "-fx-background-radius: 4; -fx-text-fill: #1F883D;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topRow.getChildren().addAll(iconLabel, nameLabel, typeLabel, sourceLabel, spacer);

        // Info row: size + upload time + associated Skills
        HBox infoRow = new HBox(16);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        if (!isManual && file.fileSize() > 0) {
            Label sizeLabel = new Label("📦 " + formatFileSize(file.fileSize()));
            sizeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #656D76;");
            infoRow.getChildren().add(sizeLabel);
        }

        Label timeLabel = new Label("🕐 " + formatTimestamp(file.uploadedAt()));
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #656D76;");
        infoRow.getChildren().add(timeLabel);

        if (file.updatedAt() > file.uploadedAt()) {
            Label updatedLabel = new Label("(更新于 " + formatTimestamp(file.updatedAt()) + ")");
            updatedLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #656D76;");
            infoRow.getChildren().add(updatedLabel);
        }

        // Associated Skills
        String associatedSkills = getAssociatedSkillNames(file.id());
        Label skillsLabel = new Label("🔗 " + (associatedSkills.isEmpty() ? "未关联 Skill" : associatedSkills));
        skillsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #656D76;");
        skillsLabel.setWrapText(true);
        infoRow.getChildren().add(skillsLabel);

        // Action row: update + edit (for manual) + delete buttons
        HBox actionRow = new HBox(8);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        if (isManual) {
            Button editButton = new Button("✏ 编辑内容");
            editButton.getStyleClass().add("btn-secondary");
            editButton.setStyle("-fx-padding: 4 12 4 12; -fx-font-size: 12px;");
            editButton.setOnAction(e -> handleEditManualContent(file));
            actionRow.getChildren().add(editButton);
        } else {
            Button updateButton = new Button("🔄 更新文件");
            updateButton.getStyleClass().add("btn-secondary");
            updateButton.setStyle("-fx-padding: 4 12 4 12; -fx-font-size: 12px;");
            updateButton.setOnAction(e -> handleUpdateFile(file));
            actionRow.getChildren().add(updateButton);
        }

        Button deleteButton = new Button("🗑 删除");
        deleteButton.setStyle("-fx-padding: 4 12 4 12; -fx-font-size: 12px; "
                + "-fx-background-color: transparent; -fx-text-fill: #CF222E; -fx-cursor: hand; "
                + "-fx-border-color: #CF222E; -fx-border-radius: 6; -fx-background-radius: 6;");
        deleteButton.setOnAction(e -> handleDeleteFile(file));
        actionRow.getChildren().add(deleteButton);

        card.getChildren().addAll(topRow, infoRow, actionRow);
        return card;
    }

    // ---- File action handlers ----

    /**
     * Handles updating an uploaded file with a new file.
     */
    private void handleUpdateFile(KnowledgeFile file) {
        if (knowledgeBaseService == null) {
            showStatus(false, "知识库服务不可用");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择替换文件");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("支持的文件", "*.pdf", "*.md", "*.txt"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        File newFile = null;
        if (uploadButton != null && uploadButton.getScene() != null
                && uploadButton.getScene().getWindow() != null) {
            newFile = fileChooser.showOpenDialog(uploadButton.getScene().getWindow());
        }

        if (newFile == null) {
            return;
        }

        // Validate format
        if (!knowledgeBaseService.isSupportedFormat(newFile.getName())) {
            showStatus(false, "不支持的文件格式（仅支持 PDF、Markdown、TXT）");
            return;
        }

        final File selectedFile = newFile;
        showStatus(true, "正在更新文件...");

        CompletableFuture.runAsync(() -> {
            try {
                knowledgeBaseService.updateFile(file.id(), selectedFile);
                Platform.runLater(() -> {
                    loadFileList();
                    showStatus(true, "文件更新成功");
                });
            } catch (FileProcessingException e) {
                LOGGER.log(Level.WARNING, "Failed to update file: " + file.fileName(), e);
                Platform.runLater(() -> {
                    showStatus(false, "文件更新失败: " + e.getMessage());
                    showExtractionFailureWithManualFallback(file.id(), e.getMessage());
                });
            }
        });
    }

    /**
     * Handles editing manual content for a knowledge file.
     */
    private void handleEditManualContent(KnowledgeFile file) {
        editingFileId = file.id();
        editTitle.setText("编辑知识内容");
        editSubtitle.setText("编辑 \"" + file.fileName() + "\" 的知识内容。");
        editTitleField.setText(file.fileName());
        editTitleField.setDisable(true); // Title cannot be changed for existing entries
        editContentField.setText(file.extractedText() != null ? file.extractedText() : "");
        hideError(editValidationError);
        showEditView();
    }

    /**
     * Handles deleting a knowledge file with confirmation.
     */
    private void handleDeleteFile(KnowledgeFile file) {
        if (knowledgeBaseService == null) {
            showStatus(false, "知识库服务不可用");
            return;
        }

        // Show confirmation dialog
        DialogHelper.showConfirmation("", 
            "确定要删除 \"" + file.fileName() + "\" 吗？\n"
                + "此操作将同时解除与所有 Skill 的关联关系，且不可恢复。")
            .ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    knowledgeBaseService.deleteFile(file.id());
                    loadFileList();
                    showStatus(true, "已删除 \"" + file.fileName() + "\"");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to delete file: " + file.fileName(), e);
                    showStatus(false, "删除失败: " + e.getMessage());
                }
            }
        });
    }

    // ---- Extraction failure handling ----

    /**
     * Shows a dialog listing extraction errors and offering manual input as fallback.
     */
    private void showExtractionFailureDialog(List<String> errors) {
        StringBuilder content = new StringBuilder();
        content.append("以下文件处理时出现错误：\n\n");
        for (String error : errors) {
            content.append("• ").append(error).append("\n");
        }
        content.append("\n您可以点击「手动输入」按钮手动添加知识内容。");
        DialogHelper.showWarning("", content.toString());
    }

    /**
     * Shows a dialog for extraction failure with option to manually input content.
     */
    private void showExtractionFailureWithManualFallback(String fileId, String errorMessage) {
        Alert alert = DialogHelper.createError("", null, 
            "错误信息: " + errorMessage + "\n\n您可以选择手动输入文本内容作为替代。");

        ButtonType manualButton = new ButtonType("手动输入", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(manualButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == manualButton) {
                // Open manual edit view for this file
                editingFileId = fileId;
                editTitle.setText("手动输入文本内容");
                editSubtitle.setText("文件提取失败，请手动输入文本内容作为替代。");
                editTitleField.setText("");
                editTitleField.setDisable(true);
                editContentField.setText("");
                hideError(editValidationError);
                showEditView();
            }
        });
    }

    // ---- View switching ----

    private void showFileListView() {
        fileListView.setVisible(true);
        fileListView.setManaged(true);
        editView.setVisible(false);
        editView.setManaged(false);
    }

    private void showEditView() {
        fileListView.setVisible(false);
        fileListView.setManaged(false);
        editView.setVisible(true);
        editView.setManaged(true);
    }

    // ---- Skill name resolution ----

    /**
     * Loads skill names into cache for displaying associated Skills.
     */
    private void loadSkillNameCache() {
        if (adminService == null) {
            return;
        }
        try {
            List<SkillAdmin> skills = adminService.getAllSkills();
            skillNameCache.clear();
            for (SkillAdmin skill : skills) {
                skillNameCache.put(skill.id(), skill.name());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load skill name cache", e);
        }
    }

    /**
     * Gets the associated Skill names for a knowledge file as a comma-separated string.
     */
    String getAssociatedSkillNames(String fileId) {
        if (bindingRepository == null) {
            return "";
        }
        try {
            List<String> skillIds = bindingRepository.getSkillIdsForFile(fileId);
            if (skillIds.isEmpty()) {
                return "";
            }
            List<String> names = new ArrayList<>();
            for (String skillId : skillIds) {
                String name = skillNameCache.get(skillId);
                names.add(name != null ? name : skillId);
            }
            return String.join(", ", names);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get associated skills for file: " + fileId, e);
            return "";
        }
    }

    // ---- Utility methods ----

    private void updateFileCount() {
        if (fileCountLabel != null) {
            fileCountLabel.setText("共 " + currentFiles.size() + " 个知识库文件");
        }
    }

    private void showStatus(boolean success, String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setManaged(true);
            statusLabel.setVisible(true);
            statusLabel.setStyle(success
                    ? "-fx-font-size: 13px; -fx-text-fill: #1F883D;"
                    : "-fx-font-size: 13px; -fx-text-fill: #CF222E;");
        }
    }

    private void showError(Label errorLabel, String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    private void hideError(Label errorLabel) {
        if (errorLabel != null) {
            errorLabel.setText("");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
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

    static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    static String getFileIcon(String fileType) {
        if (fileType == null) {
            return "📄";
        }
        return switch (fileType.toLowerCase()) {
            case "pdf" -> "📕";
            case "md" -> "📝";
            case "txt" -> "📄";
            case "manual" -> "✏️";
            default -> "📄";
        };
    }

    // ---- Package-private accessors for testing ----

    Button getBackButton() { return backButton; }
    Label getPageTitle() { return pageTitle; }
    Button getUploadButton() { return uploadButton; }
    Button getManualInputButton() { return manualInputButton; }
    StackPane getContentArea() { return contentArea; }
    VBox getFileListView() { return fileListView; }
    Label getFileCountLabel() { return fileCountLabel; }
    Label getStatusLabel() { return statusLabel; }
    VBox getFileListContainer() { return fileListContainer; }
    ScrollPane getEditView() { return editView; }
    Label getEditTitle() { return editTitle; }
    TextField getEditTitleField() { return editTitleField; }
    TextArea getEditContentField() { return editContentField; }
    Label getEditValidationError() { return editValidationError; }
    Button getEditSaveButton() { return editSaveButton; }
    List<KnowledgeFile> getCurrentFiles() { return currentFiles; }
    String getEditingFileId() { return editingFileId; }
    Map<String, String> getSkillNameCache() { return skillNameCache; }
}
