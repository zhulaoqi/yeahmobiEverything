package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.admin.AdminService;
import com.yeahmobi.everything.admin.KnowledgeSkillTemplate;
import com.yeahmobi.everything.admin.SkillTemplate;
import com.yeahmobi.everything.knowledge.FileProcessingException;
import com.yeahmobi.everything.knowledge.KnowledgeBaseService;
import com.yeahmobi.everything.knowledge.KnowledgeFile;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Skill creation wizard (skill-wizard.fxml).
 * <p>
 * Supports two creation flows:
 * <ul>
 *   <li><b>General Skill</b>: Single form with name, description, category, icon,
 *       type (GENERAL/INTERNAL), and Prompt template.</li>
 *   <li><b>Knowledge Skill</b>: Multi-step wizard:
 *       Step 1 - Upload documents / manual input →
 *       Step 2 - Configure basic info →
 *       Step 3 - Configure Prompt template →
 *       Step 4 - Preview &amp; publish.</li>
 * </ul>
 * </p>
 */
public class SkillWizardController {

    private static final Logger LOGGER = Logger.getLogger(SkillWizardController.class.getName());

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "翻译", "写作", "开发", "数据分析", "客服", "营销", "其他");

    // ---- FXML bindings ----

    @FXML private Button backButton;
    @FXML private Label wizardTitle;
    @FXML private Label stepIndicator;
    @FXML private StackPane wizardContent;

    // Step 1: Select Skill Kind
    @FXML private VBox stepSelectKind;
    @FXML private VBox generalSkillCard;
    @FXML private VBox knowledgeSkillCard;

    // General Skill form
    @FXML private ScrollPane stepGeneralForm;
    @FXML private Label generalNameLabel;
    @FXML private TextField generalNameField;
    @FXML private Label generalDescLabel;
    @FXML private TextArea generalDescField;
    @FXML private Label generalCategoryLabel;
    @FXML private ComboBox<String> generalCategoryCombo;
    @FXML private TextField generalIconField;
    @FXML private ToggleGroup generalTypeGroup;
    @FXML private RadioButton generalTypeGeneral;
    @FXML private RadioButton generalTypeInternal;
    @FXML private ToggleGroup generalExecutionGroup;
    @FXML private RadioButton generalExecSingle;
    @FXML private RadioButton generalExecMulti;
    @FXML private TextArea generalPromptField;
    @FXML private TextField generalPreviewInput;
    @FXML private Label generalPreviewResult;
    @FXML private Label generalValidationError;
    @FXML private Button generalSubmitButton;

    // Knowledge Skill Step 1: Upload / Manual input
    @FXML private ScrollPane stepKnowledgeUpload;
    @FXML private VBox dropZone;
    @FXML private VBox uploadedFilesList;
    @FXML private TextArea manualContentField;
    @FXML private Button knowledgeUploadNextButton;

    // Knowledge Skill Step 2: Basic info
    @FXML private ScrollPane stepKnowledgeInfo;
    @FXML private Label knowledgeNameLabel;
    @FXML private TextField knowledgeNameField;
    @FXML private Label knowledgeDescLabel;
    @FXML private TextArea knowledgeDescField;
    @FXML private Label knowledgeCategoryLabel;
    @FXML private ComboBox<String> knowledgeCategoryCombo;
    @FXML private TextField knowledgeIconField;
    @FXML private ToggleGroup knowledgeTypeGroup;
    @FXML private RadioButton knowledgeTypeGeneral;
    @FXML private RadioButton knowledgeTypeInternal;
    @FXML private ToggleGroup knowledgeExecutionGroup;
    @FXML private RadioButton knowledgeExecSingle;
    @FXML private RadioButton knowledgeExecMulti;
    @FXML private Label knowledgeInfoValidationError;

    // Knowledge Skill Step 3: Prompt template
    @FXML private ScrollPane stepKnowledgePrompt;
    @FXML private TextArea knowledgePromptField;
    @FXML private TextField knowledgePreviewInput;
    @FXML private Label knowledgePreviewResult;

    // Knowledge Skill Step 4: Preview & Publish
    @FXML private ScrollPane stepKnowledgePreview;
    @FXML private Label previewName;
    @FXML private Label previewDesc;
    @FXML private Label previewCategory;
    @FXML private Label previewType;
    @FXML private Label previewKnowledgeSummary;
    @FXML private Label previewPrompt;
    @FXML private Label knowledgePublishError;
    @FXML private ProgressIndicator publishProgress;
    @FXML private Button publishButton;

    // ---- Dependencies ----

    private AdminService adminService;
    private KnowledgeBaseService knowledgeBaseService;

    /** Callback invoked when the user clicks back or finishes the wizard. */
    private Runnable onBackCallback;

    /** Callback invoked when a Skill is successfully created. */
    private java.util.function.Consumer<String> onSkillCreatedCallback;

    // ---- Internal state ----

    /** Uploaded knowledge files (file IDs returned from KnowledgeBaseService). */
    private final List<String> uploadedFileIds = new ArrayList<>();

    /** Files pending upload (selected but not yet uploaded). */
    private final List<File> pendingFiles = new ArrayList<>();

    /** Tracks which files have been uploaded successfully. */
    private final List<UploadedFileInfo> uploadedFilesInfo = new ArrayList<>();

    // ---- Dependency injection ----

    public void setAdminService(AdminService adminService) {
        this.adminService = adminService;
    }

    public void setKnowledgeBaseService(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public void setOnBackCallback(Runnable callback) {
        this.onBackCallback = callback;
    }

    public void setOnSkillCreatedCallback(java.util.function.Consumer<String> callback) {
        this.onSkillCreatedCallback = callback;
    }

    // ---- Initialization ----

    @FXML
    public void initialize() {
        // Populate category combo boxes
        if (generalCategoryCombo != null) {
            generalCategoryCombo.getItems().addAll(DEFAULT_CATEGORIES);
        }
        if (knowledgeCategoryCombo != null) {
            knowledgeCategoryCombo.getItems().addAll(DEFAULT_CATEGORIES);
        }
        showStep(stepSelectKind);
        updateStepIndicator("");
    }

    // ---- Navigation: Back ----

    @FXML
    public void onBack() {
        if (onBackCallback != null) {
            onBackCallback.run();
        }
    }

    // ---- Step 1: Select Skill Kind ----

    @FXML
    public void onSelectGeneralSkill() {
        showStep(stepGeneralForm);
        wizardTitle.setText("创建通用型 Skill");
        updateStepIndicator("");
    }

    @FXML
    public void onSelectKnowledgeSkill() {
        showStep(stepKnowledgeUpload);
        wizardTitle.setText("创建知识型 Skill");
        updateStepIndicator("步骤 1/4");
    }

    // ---- General Skill form ----

    @FXML
    public void onPreviewGeneralPrompt() {
        String template = generalPromptField.getText();
        String sampleInput = generalPreviewInput.getText();
        previewPromptTemplate(template, sampleInput, generalPreviewResult);
    }

    @FXML
    public void onSubmitGeneralSkill() {
        clearValidationHighlights(generalNameLabel, generalDescLabel, generalCategoryLabel);
        hideError(generalValidationError);

        String name = generalNameField.getText();
        String desc = generalDescField.getText();
        String category = generalCategoryCombo.getValue();
        String icon = generalIconField.getText();
        String prompt = generalPromptField.getText();
        SkillType type = generalTypeInternal.isSelected() ? SkillType.INTERNAL : SkillType.GENERAL;

        // Validate required fields
        List<String> missingFields = new ArrayList<>();
        if (name == null || name.isBlank()) {
            missingFields.add("名称");
            highlightField(generalNameLabel);
        }
        if (desc == null || desc.isBlank()) {
            missingFields.add("描述");
            highlightField(generalDescLabel);
        }
        if (category == null || category.isBlank()) {
            missingFields.add("分类");
            highlightField(generalCategoryLabel);
        }

        if (!missingFields.isEmpty()) {
            showError(generalValidationError, "请填写必填字段：" + String.join("、", missingFields));
            return;
        }

        if (adminService == null) {
            showError(generalValidationError, "管理服务不可用");
            return;
        }

        generalSubmitButton.setDisable(true);

        CompletableFuture.supplyAsync(() -> {
            SkillExecutionMode mode = generalExecMulti != null && generalExecMulti.isSelected()
                    ? SkillExecutionMode.MULTI
                    : SkillExecutionMode.SINGLE;
            SkillTemplate template = new SkillTemplate(name.trim(), desc.trim(),
                    category.trim(), icon != null ? icon.trim() : "", type, prompt, mode);
            return adminService.createSkillFromTemplate(template);
        }).thenAcceptAsync(result -> {
            generalSubmitButton.setDisable(false);
            if (result.success()) {
                String createdId = result.skill() != null ? result.skill().id() : null;
                showSuccessAndReturn(result.message(), createdId);
            } else {
                showError(generalValidationError, result.message());
            }
        }, Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> {
                generalSubmitButton.setDisable(false);
                showError(generalValidationError, "创建失败: " + ex.getMessage());
            });
            LOGGER.log(Level.WARNING, "Failed to create general skill", ex);
            return null;
        });
    }

    // ---- Knowledge Skill Step 1: Upload / Manual input ----

    @FXML
    public void onDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            dropZone.setStyle(
                    "-fx-padding: 32; -fx-border-color: #0969DA; -fx-border-width: 2; -fx-border-style: dashed; " +
                    "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: #DDF4FF; -fx-cursor: hand;");
        }
        event.consume();
    }

    @FXML
    public void onDragExited(DragEvent event) {
        dropZone.setStyle(
                "-fx-padding: 32; -fx-border-color: #D0D7DE; -fx-border-width: 2; -fx-border-style: dashed; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: #F6F8FA; -fx-cursor: hand;");
        event.consume();
    }

    @FXML
    public void onDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            addFilesToUpload(db.getFiles());
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
        // Reset drop zone style
        onDragExited(event);
    }

    @FXML
    public void onBrowseFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择知识库文件");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("支持的文件", "*.pdf", "*.md", "*.txt"),
                new FileChooser.ExtensionFilter("PDF 文件", "*.pdf"),
                new FileChooser.ExtensionFilter("Markdown 文件", "*.md"),
                new FileChooser.ExtensionFilter("文本文件", "*.txt"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        Node source = dropZone;
        List<File> files = null;
        if (source != null && source.getScene() != null && source.getScene().getWindow() != null) {
            files = fileChooser.showOpenMultipleDialog(source.getScene().getWindow());
        }

        if (files != null && !files.isEmpty()) {
            addFilesToUpload(files);
        }
    }

    @FXML
    public void onKnowledgeBackToKindSelect() {
        showStep(stepSelectKind);
        wizardTitle.setText("创建 Skill");
        updateStepIndicator("");
    }

    @FXML
    public void onKnowledgeUploadNext() {
        // Upload pending files first, then proceed
        if (!pendingFiles.isEmpty()) {
            uploadPendingFiles(() -> Platform.runLater(() -> {
                showStep(stepKnowledgeInfo);
                updateStepIndicator("步骤 2/4");
            }));
        } else {
            showStep(stepKnowledgeInfo);
            updateStepIndicator("步骤 2/4");
        }
    }

    // ---- Knowledge Skill Step 2: Basic info ----

    @FXML
    public void onKnowledgeInfoBack() {
        showStep(stepKnowledgeUpload);
        updateStepIndicator("步骤 1/4");
    }

    @FXML
    public void onKnowledgeInfoNext() {
        clearValidationHighlights(knowledgeNameLabel, knowledgeDescLabel, knowledgeCategoryLabel);
        hideError(knowledgeInfoValidationError);

        String name = knowledgeNameField.getText();
        String desc = knowledgeDescField.getText();
        String category = knowledgeCategoryCombo.getValue();

        List<String> missingFields = new ArrayList<>();
        if (name == null || name.isBlank()) {
            missingFields.add("名称");
            highlightField(knowledgeNameLabel);
        }
        if (desc == null || desc.isBlank()) {
            missingFields.add("描述");
            highlightField(knowledgeDescLabel);
        }
        if (category == null || category.isBlank()) {
            missingFields.add("分类");
            highlightField(knowledgeCategoryLabel);
        }

        if (!missingFields.isEmpty()) {
            showError(knowledgeInfoValidationError, "请填写必填字段：" + String.join("、", missingFields));
            return;
        }

        showStep(stepKnowledgePrompt);
        updateStepIndicator("步骤 3/4");
    }

    // ---- Knowledge Skill Step 3: Prompt template ----

    @FXML
    public void onPreviewKnowledgePrompt() {
        String template = knowledgePromptField.getText();
        String sampleInput = knowledgePreviewInput.getText();
        previewPromptTemplate(template, sampleInput, knowledgePreviewResult);
    }

    @FXML
    public void onKnowledgePromptBack() {
        showStep(stepKnowledgeInfo);
        updateStepIndicator("步骤 2/4");
    }

    @FXML
    public void onKnowledgePromptNext() {
        // Populate preview fields
        previewName.setText(knowledgeNameField.getText());
        previewDesc.setText(knowledgeDescField.getText());
        previewCategory.setText(knowledgeCategoryCombo.getValue());
        previewType.setText(knowledgeTypeInternal.isSelected() ? "内部 Skill" : "通用 Skill");

        // Build knowledge summary
        StringBuilder summary = new StringBuilder();
        if (!uploadedFilesInfo.isEmpty()) {
            summary.append(uploadedFilesInfo.size()).append(" 个文件");
        }
        String manualContent = manualContentField.getText();
        if (manualContent != null && !manualContent.isBlank()) {
            if (summary.length() > 0) {
                summary.append(" + ");
            }
            summary.append("手动输入内容");
        }
        if (summary.length() == 0) {
            summary.append("无");
        }
        previewKnowledgeSummary.setText(summary.toString());

        String prompt = knowledgePromptField.getText();
        previewPrompt.setText(prompt != null && !prompt.isBlank() ? prompt : "（未设置）");

        showStep(stepKnowledgePreview);
        updateStepIndicator("步骤 4/4");
    }

    // ---- Knowledge Skill Step 4: Preview & Publish ----

    @FXML
    public void onKnowledgePreviewBack() {
        showStep(stepKnowledgePrompt);
        updateStepIndicator("步骤 3/4");
    }

    @FXML
    public void onPublishKnowledgeSkill() {
        hideError(knowledgePublishError);

        if (adminService == null) {
            showError(knowledgePublishError, "管理服务不可用");
            return;
        }

        publishButton.setDisable(true);
        publishProgress.setVisible(true);
        publishProgress.setManaged(true);

        String name = knowledgeNameField.getText().trim();
        String desc = knowledgeDescField.getText().trim();
        String category = knowledgeCategoryCombo.getValue().trim();
        String icon = knowledgeIconField.getText() != null ? knowledgeIconField.getText().trim() : "";
        String prompt = knowledgePromptField.getText();
        SkillType type = knowledgeTypeInternal.isSelected() ? SkillType.INTERNAL : SkillType.GENERAL;
        String manualContent = manualContentField.getText();

        CompletableFuture.supplyAsync(() -> {
            SkillExecutionMode mode = knowledgeExecMulti != null && knowledgeExecMulti.isSelected()
                    ? SkillExecutionMode.MULTI
                    : SkillExecutionMode.SINGLE;
            KnowledgeSkillTemplate template = new KnowledgeSkillTemplate(
                    name, desc, category, icon, type, prompt,
                    new ArrayList<>(uploadedFileIds),
                    manualContent != null && !manualContent.isBlank() ? manualContent.trim() : null,
                    mode
            );
            return adminService.createKnowledgeSkill(template);
        }).thenAcceptAsync(result -> {
            publishButton.setDisable(false);
            publishProgress.setVisible(false);
            publishProgress.setManaged(false);
            if (result.success()) {
                String createdId = result.skill() != null ? result.skill().id() : null;
                showSuccessAndReturn(result.message(), createdId);
            } else {
                showError(knowledgePublishError, result.message());
            }
        }, Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> {
                publishButton.setDisable(false);
                publishProgress.setVisible(false);
                publishProgress.setManaged(false);
                showError(knowledgePublishError, "发布失败: " + ex.getMessage());
            });
            LOGGER.log(Level.WARNING, "Failed to publish knowledge skill", ex);
            return null;
        });
    }

    // ---- File upload helpers ----

    /**
     * Adds files to the pending upload list and renders them in the UI.
     * Validates file format before adding.
     */
    void addFilesToUpload(List<File> files) {
        for (File file : files) {
            if (knowledgeBaseService != null && !knowledgeBaseService.isSupportedFormat(file.getName())) {
                addFileRowError(file.getName(), "不支持的文件格式");
                continue;
            }
            // Avoid duplicates
            boolean alreadyAdded = pendingFiles.stream()
                    .anyMatch(f -> f.getAbsolutePath().equals(file.getAbsolutePath()));
            boolean alreadyUploaded = uploadedFilesInfo.stream()
                    .anyMatch(info -> info.fileName.equals(file.getName()));
            if (alreadyAdded || alreadyUploaded) {
                continue;
            }
            pendingFiles.add(file);
            addFileRow(file.getName(), "待上传", false);
        }
    }

    /**
     * Uploads all pending files asynchronously, updating progress in the UI.
     */
    private void uploadPendingFiles(Runnable onComplete) {
        if (knowledgeBaseService == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        List<File> toUpload = new ArrayList<>(pendingFiles);
        pendingFiles.clear();

        CompletableFuture.runAsync(() -> {
            for (File file : toUpload) {
                try {
                    KnowledgeFile uploaded = knowledgeBaseService.uploadFile(file);
                    uploadedFileIds.add(uploaded.id());
                    uploadedFilesInfo.add(new UploadedFileInfo(uploaded.id(), file.getName()));
                    Platform.runLater(() -> updateFileRowStatus(file.getName(), "✅ 已上传"));
                } catch (FileProcessingException e) {
                    LOGGER.log(Level.WARNING, "Failed to upload file: " + file.getName(), e);
                    Platform.runLater(() -> updateFileRowStatus(file.getName(), "❌ 上传失败"));
                }
            }
        }).thenRunAsync(() -> {
            if (onComplete != null) onComplete.run();
        });
    }

    /**
     * Adds a file row to the uploaded files list UI.
     */
    private void addFileRow(String fileName, String status, boolean isError) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 8; -fx-background-color: #F6F8FA; -fx-background-radius: 6;");
        row.setUserData(fileName);

        Label icon = new Label("📄");
        Label nameLabel = new Label(fileName);
        nameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1F2328;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusLabel = new Label(status);
        statusLabel.setStyle(isError
                ? "-fx-font-size: 12px; -fx-text-fill: #CF222E;"
                : "-fx-font-size: 12px; -fx-text-fill: #656D76;");
        statusLabel.setUserData("status");

        Button removeBtn = new Button("✕");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #656D76; -fx-cursor: hand; -fx-padding: 2 6 2 6;");
        removeBtn.setOnAction(e -> removeFileRow(fileName));

        row.getChildren().addAll(icon, nameLabel, spacer, statusLabel, removeBtn);
        uploadedFilesList.getChildren().add(row);
    }

    /**
     * Adds an error row for an unsupported file.
     */
    private void addFileRowError(String fileName, String errorMessage) {
        addFileRow(fileName + " - " + errorMessage, "❌", true);
    }

    /**
     * Updates the status label of a file row.
     */
    private void updateFileRowStatus(String fileName, String newStatus) {
        for (Node node : uploadedFilesList.getChildren()) {
            if (node instanceof HBox row && fileName.equals(row.getUserData())) {
                for (Node child : row.getChildren()) {
                    if (child instanceof Label label && "status".equals(label.getUserData())) {
                        label.setText(newStatus);
                        if (newStatus.startsWith("✅")) {
                            label.setStyle("-fx-font-size: 12px; -fx-text-fill: #1F883D;");
                        } else if (newStatus.startsWith("❌")) {
                            label.setStyle("-fx-font-size: 12px; -fx-text-fill: #CF222E;");
                        }
                        break;
                    }
                }
                break;
            }
        }
    }

    /**
     * Removes a file row from the UI and the pending/uploaded lists.
     */
    private void removeFileRow(String fileName) {
        uploadedFilesList.getChildren().removeIf(node ->
                node instanceof HBox row && fileName.equals(row.getUserData()));
        pendingFiles.removeIf(f -> f.getName().equals(fileName));
        uploadedFilesInfo.removeIf(info -> info.fileName.equals(fileName));
        uploadedFileIds.removeIf(id -> {
            for (UploadedFileInfo info : uploadedFilesInfo) {
                if (info.fileId.equals(id)) return false;
            }
            return true;
        });
    }

    // ---- Prompt preview helper ----

    private void previewPromptTemplate(String template, String sampleInput, Label resultLabel) {
        if (template == null || template.isBlank()) {
            resultLabel.setText("请先输入 Prompt 模板");
            resultLabel.setVisible(true);
            resultLabel.setManaged(true);
            return;
        }

        String rendered;
        if (adminService != null) {
            rendered = adminService.renderPromptTemplate(template,
                    sampleInput != null ? sampleInput : "");
        } else {
            rendered = template.replace("{{user_input}}",
                    sampleInput != null ? sampleInput : "");
        }

        resultLabel.setText(rendered);
        resultLabel.setVisible(true);
        resultLabel.setManaged(true);
    }

    // ---- Step switching ----

    /**
     * Shows the specified step and hides all others.
     */
    private void showStep(Node stepToShow) {
        for (Node child : wizardContent.getChildren()) {
            boolean show = child == stepToShow;
            child.setVisible(show);
            child.setManaged(show);
        }
    }

    private void updateStepIndicator(String text) {
        if (stepIndicator != null) {
            stepIndicator.setText(text);
        }
    }

    // ---- Validation helpers ----

    /**
     * Highlights a label to indicate a missing required field.
     */
    private void highlightField(Label label) {
        if (label != null) {
            label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #CF222E;");
        }
    }

    /**
     * Clears validation highlights from the given labels.
     */
    private void clearValidationHighlights(Label... labels) {
        for (Label label : labels) {
            if (label != null) {
                label.setStyle("");
                label.getStyleClass().removeAll("login-error-label");
                if (!label.getStyleClass().contains("card-title")) {
                    label.getStyleClass().add("card-title");
                }
            }
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

    /**
     * Shows a success alert and navigates back.
     */
    private void showSuccessAndReturn(String message, String createdSkillId) {
        DialogHelper.showInformation("", message);

        if (onSkillCreatedCallback != null && createdSkillId != null && !createdSkillId.isBlank()) {
            onSkillCreatedCallback.accept(createdSkillId);
            return;
        }
        if (onBackCallback != null) {
            onBackCallback.run();
        }
    }

    // ---- Internal data class ----

    /**
     * Tracks uploaded file information.
     */
    static class UploadedFileInfo {
        final String fileId;
        final String fileName;

        UploadedFileInfo(String fileId, String fileName) {
            this.fileId = fileId;
            this.fileName = fileName;
        }
    }

    // ---- Package-private accessors for testing ----

    Button getBackButton() { return backButton; }
    Label getWizardTitle() { return wizardTitle; }
    Label getStepIndicator() { return stepIndicator; }
    StackPane getWizardContent() { return wizardContent; }
    VBox getStepSelectKind() { return stepSelectKind; }
    ScrollPane getStepGeneralForm() { return stepGeneralForm; }
    ScrollPane getStepKnowledgeUpload() { return stepKnowledgeUpload; }
    ScrollPane getStepKnowledgeInfo() { return stepKnowledgeInfo; }
    ScrollPane getStepKnowledgePrompt() { return stepKnowledgePrompt; }
    ScrollPane getStepKnowledgePreview() { return stepKnowledgePreview; }
    TextField getGeneralNameField() { return generalNameField; }
    TextArea getGeneralDescField() { return generalDescField; }
    ComboBox<String> getGeneralCategoryCombo() { return generalCategoryCombo; }
    TextField getGeneralIconField() { return generalIconField; }
    RadioButton getGeneralTypeGeneral() { return generalTypeGeneral; }
    RadioButton getGeneralTypeInternal() { return generalTypeInternal; }
    TextArea getGeneralPromptField() { return generalPromptField; }
    Label getGeneralValidationError() { return generalValidationError; }
    Button getGeneralSubmitButton() { return generalSubmitButton; }
    TextField getKnowledgeNameField() { return knowledgeNameField; }
    TextArea getKnowledgeDescField() { return knowledgeDescField; }
    ComboBox<String> getKnowledgeCategoryCombo() { return knowledgeCategoryCombo; }
    TextField getKnowledgeIconField() { return knowledgeIconField; }
    RadioButton getKnowledgeTypeGeneral() { return knowledgeTypeGeneral; }
    RadioButton getKnowledgeTypeInternal() { return knowledgeTypeInternal; }
    Label getKnowledgeInfoValidationError() { return knowledgeInfoValidationError; }
    TextArea getKnowledgePromptField() { return knowledgePromptField; }
    TextArea getManualContentField() { return manualContentField; }
    VBox getUploadedFilesList() { return uploadedFilesList; }
    Label getPreviewName() { return previewName; }
    Label getPreviewDesc() { return previewDesc; }
    Label getPreviewCategory() { return previewCategory; }
    Label getPreviewType() { return previewType; }
    Label getPreviewKnowledgeSummary() { return previewKnowledgeSummary; }
    Label getPreviewPrompt() { return previewPrompt; }
    Label getKnowledgePublishError() { return knowledgePublishError; }
    Button getPublishButton() { return publishButton; }
    ProgressIndicator getPublishProgress() { return publishProgress; }
    List<String> getUploadedFileIds() { return uploadedFileIds; }
    List<UploadedFileInfo> getUploadedFilesInfo() { return uploadedFilesInfo; }
    List<File> getPendingFiles() { return pendingFiles; }
}
