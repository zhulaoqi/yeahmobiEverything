package com.yeahmobi.everything.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yeahmobi.everything.personalskill.PersonalSkill;
import com.yeahmobi.everything.personalskill.PersonalSkillResult;
import com.yeahmobi.everything.personalskill.PersonalSkillService;
import com.yeahmobi.everything.personalskill.PersonalSkillStatus;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Controller for personal skill creation and review submission.
 */
public class PersonalSkillController {

    @FXML private TextField nameField;
    @FXML private TextField categoryField;
    @FXML private TextArea descriptionArea;
    @FXML private TextArea promptArea;
    @FXML private Label resultLabel;
    @FXML private Button saveButton;
    @FXML private Button submitButton;
    @FXML private VBox skillListContainer;

    private PersonalSkillService personalSkillService;
    private String userId;
    private String currentSkillId;
    private List<PersonalSkill> currentSkills = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void setPersonalSkillService(PersonalSkillService personalSkillService) {
        this.personalSkillService = personalSkillService;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @FXML
    public void initialize() {
        if (resultLabel != null) {
            resultLabel.setManaged(false);
            resultLabel.setVisible(false);
        }
    }

    public void loadSkills() {
        if (personalSkillService == null || userId == null) {
            return;
        }
        currentSkills = personalSkillService.listByUser(userId);
        renderSkillList();
    }

    @FXML
    public void onSaveDraft() {
        if (personalSkillService == null) {
            showResult(false, "服务不可用");
            return;
        }
        PersonalSkillResult result = personalSkillService.saveDraft(
                userId,
                nameField.getText(),
                descriptionArea.getText(),
                categoryField.getText(),
                promptArea.getText(),
                currentSkillId
        );
        showResult(result.success(), result.message());
        if (result.success() && result.skill() != null) {
            currentSkillId = result.skill().id();
            loadSkills();
        }
    }

    @FXML
    public void onSubmitReview() {
        if (personalSkillService == null) {
            showResult(false, "服务不可用");
            return;
        }
        if (currentSkillId == null || currentSkillId.isBlank()) {
            showResult(false, "请先保存草稿再提交审核");
            return;
        }
        PersonalSkillResult result = personalSkillService.submitForReview(userId, currentSkillId);
        showResult(result.success(), result.message());
        if (result.success()) {
            loadSkills();
        }
    }

    @FXML
    public void onImportPackage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导入技能包");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Skill Package", "*.json")
        );
        var window = skillListContainer.getScene() != null ? skillListContainer.getScene().getWindow() : null;
        var file = chooser.showOpenDialog(window);
        if (file == null) {
            return;
        }
        try {
            String json = java.nio.file.Files.readString(file.toPath());
            JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
            String name = getString(payload, "name");
            String description = getString(payload, "description");
            String category = getString(payload, "category");
            String promptTemplate = getString(payload, "promptTemplate");

            nameField.setText(name);
            descriptionArea.setText(description);
            categoryField.setText(category);
            promptArea.setText(promptTemplate);
            currentSkillId = null;
            showResult(true, "已导入技能包，保存后可继续编辑");
        } catch (Exception e) {
            showResult(false, "导入失败，请检查技能包格式");
        }
    }

    private void renderSkillList() {
        skillListContainer.getChildren().clear();
        if (currentSkills.isEmpty()) {
            Label empty = new Label("暂无个人 Skill，创建后会显示在这里");
            empty.getStyleClass().add("section-subtitle");
            skillListContainer.getChildren().add(empty);
            return;
        }
        for (PersonalSkill skill : currentSkills) {
            skillListContainer.getChildren().add(createSkillRow(skill));
        }
    }

    private VBox createSkillRow(PersonalSkill skill) {
        VBox card = new VBox(6);
        card.getStyleClass().add("skill-card");
        card.setStyle("-fx-padding: 12; -fx-border-color: #D0D7DE; -fx-border-radius: 6; -fx-background-radius: 6;");

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(skill.name());
        nameLabel.getStyleClass().add("card-title");

        Label statusBadge = new Label(mapStatus(skill.status()));
        statusBadge.getStyleClass().add("skill-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button editBtn = new Button("编辑");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setOnAction(e -> loadToForm(skill));

        Button exportBtn = new Button("导出技能包");
        exportBtn.getStyleClass().add("btn-secondary");
        exportBtn.setOnAction(e -> exportSkillPackage(skill));

        Button shareBtn = new Button("分享");
        shareBtn.getStyleClass().add("btn-secondary");
        shareBtn.setOnAction(e -> shareSkillPackage(skill));

        Button submitBtn = new Button("提交审核");
        submitBtn.getStyleClass().add("btn-primary");
        submitBtn.setOnAction(e -> {
            currentSkillId = skill.id();
            onSubmitReview();
        });
        submitBtn.setVisible(skill.status() == PersonalSkillStatus.DRAFT || skill.status() == PersonalSkillStatus.REJECTED);
        submitBtn.setManaged(submitBtn.isVisible());

        topRow.getChildren().addAll(nameLabel, statusBadge, spacer, editBtn, exportBtn, shareBtn, submitBtn);

        Label meta = new Label("分类: " + skill.category());
        meta.getStyleClass().add("section-subtitle");

        Label desc = new Label(skill.description());
        desc.getStyleClass().add("card-description");
        desc.setWrapText(true);

        card.getChildren().addAll(topRow, meta, desc);
        if (skill.reviewerNote() != null && !skill.reviewerNote().isBlank()) {
            Label note = new Label("审核备注: " + skill.reviewerNote());
            note.getStyleClass().add("section-subtitle");
            card.getChildren().add(note);
        }

        return card;
    }

    private void loadToForm(PersonalSkill skill) {
        currentSkillId = skill.id();
        nameField.setText(skill.name());
        categoryField.setText(skill.category());
        descriptionArea.setText(skill.description());
        promptArea.setText(skill.promptTemplate());
        showResult(true, "已载入草稿，可继续编辑");
    }

    private String mapStatus(PersonalSkillStatus status) {
        return switch (status) {
            case DRAFT -> "草稿";
            case PENDING -> "审核中";
            case APPROVED -> "已通过";
            case REJECTED -> "已驳回";
        };
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

    private void exportSkillPackage(PersonalSkill skill) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出技能包");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Skill Package", "*.json")
        );
        chooser.setInitialFileName(skill.name() + "-skill.json");
        var window = skillListContainer.getScene() != null ? skillListContainer.getScene().getWindow() : null;
        var file = chooser.showSaveDialog(window);
        if (file == null) {
            return;
        }

        Map<String, Object> payload = buildSkillPackagePayload(skill);
        try {
            java.nio.file.Files.writeString(file.toPath(), gson.toJson(payload));
            showResult(true, "技能包已导出");
        } catch (Exception e) {
            showResult(false, "导出失败，请重试");
        }
    }

    private void shareSkillPackage(PersonalSkill skill) {
        Map<String, Object> payload = buildSkillPackagePayload(skill);
        ClipboardContent content = new ClipboardContent();
        content.putString(gson.toJson(payload));
        Clipboard.getSystemClipboard().setContent(content);
        showResult(true, "技能包已复制，可直接分享");
    }

    private Map<String, Object> buildSkillPackagePayload(PersonalSkill skill) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "1.0");
        payload.put("type", "personal-skill");
        payload.put("id", skill.id());
        payload.put("ownerUserId", skill.userId());
        payload.put("name", skill.name());
        payload.put("description", skill.description());
        payload.put("category", skill.category());
        payload.put("promptTemplate", skill.promptTemplate());
        payload.put("createdAt", skill.createdAt());
        payload.put("updatedAt", skill.updatedAt());
        return payload;
    }

    private String getString(JsonObject payload, String key) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return "";
        }
        return payload.get(key).getAsString();
    }
}
