package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.common.NetworkException;
import com.yeahmobi.everything.personalskill.PersonalSkillService;
import com.yeahmobi.everything.skill.Skill;
import com.yeahmobi.everything.skill.SkillService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Marketplace for public skills (browse, import to personal).
 */
public class MarketSkillController {

    @FXML private TextField searchField;
    @FXML private Label resultLabel;
    @FXML private VBox marketListContainer;

    private SkillService skillService;
    private PersonalSkillService personalSkillService;
    private String userId;
    private List<Skill> allSkills = new ArrayList<>();
    private SkillController.SkillClickCallback onSkillClick;

    public void setSkillService(SkillService skillService) {
        this.skillService = skillService;
    }

    public void setPersonalSkillService(PersonalSkillService personalSkillService) {
        this.personalSkillService = personalSkillService;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setOnSkillClick(SkillController.SkillClickCallback onSkillClick) {
        this.onSkillClick = onSkillClick;
    }

    @FXML
    public void initialize() {
        if (resultLabel != null) {
            resultLabel.setManaged(false);
            resultLabel.setVisible(false);
        }
    }

    public void loadSkills() {
        if (skillService == null) {
            showResult(false, "技能服务不可用");
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return skillService.fetchSkills();
            } catch (NetworkException e) {
                throw new RuntimeException(e);
            }
        }).thenAcceptAsync(skills -> {
            allSkills = new ArrayList<>(skills);
            renderList(allSkills);
        }, Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> showResult(false, "加载失败，请稍后重试"));
            return null;
        });
    }

    @FXML
    public void onSearchChanged() {
        String keyword = searchField.getText();
        if (keyword == null || keyword.isBlank()) {
            renderList(allSkills);
            return;
        }
        List<Skill> filtered = skillService.searchSkills(keyword.trim(), allSkills);
        renderList(filtered);
    }

    private void renderList(List<Skill> skills) {
        marketListContainer.getChildren().clear();
        if (skills.isEmpty()) {
            Label empty = new Label("暂无可用技能");
            empty.getStyleClass().add("section-subtitle");
            marketListContainer.getChildren().add(empty);
            return;
        }
        for (Skill skill : skills) {
            marketListContainer.getChildren().add(createRow(skill));
        }
    }

    private VBox createRow(Skill skill) {
        VBox card = new VBox(6);
        card.getStyleClass().add("skill-card");
        card.setStyle("-fx-padding: 12; -fx-border-color: #D0D7DE; -fx-border-radius: 6; -fx-background-radius: 6;");

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        SkillI18nZhCn zh = SkillI18nUtil.zhCn(skill);
        String displayName = (zh != null && zh.displayName() != null) ? zh.displayName() : skill.name();
        String oneLine = (zh != null && zh.oneLine() != null) ? zh.oneLine() : skill.description();

        Label nameLabel = new Label(displayName != null ? displayName : "");
        nameLabel.getStyleClass().add("card-title");

        Label categoryLabel = new Label(skill.category());
        categoryLabel.getStyleClass().add("skill-category");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button importBtn = new Button("添加到个人");
        importBtn.getStyleClass().add("btn-primary");
        importBtn.setOnAction(e -> {
            addToPersonal(skill, importBtn);
            e.consume();
        });

        topRow.getChildren().addAll(nameLabel, categoryLabel, spacer, importBtn);

        Label desc = new Label(oneLine != null ? oneLine : "");
        desc.getStyleClass().add("card-description");
        desc.setWrapText(true);

        card.getChildren().addAll(topRow, desc);
        card.setOnMouseClicked(e -> {
            if (onSkillClick != null) {
                onSkillClick.onSkillClicked(skill);
            }
        });
        return card;
    }

    private void addToPersonal(Skill skill, Button importBtn) {
        if (personalSkillService == null) {
            showResult(false, "个人技能服务不可用");
            return;
        }
        if (userId == null || userId.isBlank()) {
            showResult(false, "用户信息缺失，请重新登录");
            return;
        }
        String prompt = (skill.promptTemplate() == null || skill.promptTemplate().isBlank())
                ? "你是一个专业助手，请根据用户输入完成任务：\n{{input}}"
                : skill.promptTemplate();

        importBtn.setDisable(true);
        CompletableFuture.supplyAsync(() ->
                personalSkillService.saveDraft(
                        userId,
                        skill.name(),
                        skill.description(),
                        skill.category(),
                        prompt,
                        null
                )
        ).thenAcceptAsync(result -> {
            showResult(result.success(), result.message());
            importBtn.setDisable(false);
        }, Platform::runLater).exceptionally(ex -> {
            Platform.runLater(() -> {
                showResult(false, "添加失败，请稍后重试");
                importBtn.setDisable(false);
            });
            return null;
        });
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
}
