package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.hrassist.CandidateCase;
import com.yeahmobi.everything.hrassist.HrCaseService;
import com.yeahmobi.everything.hrassist.HrCaseStage;
import com.yeahmobi.everything.hrassist.HrCaseStatus;
import com.yeahmobi.everything.hrassist.HrRiskLevel;
import com.yeahmobi.everything.workfollowup.WorkFollowupService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * HR workbench for stage/risk/next-action orchestration.
 */
public class HrWorkbenchController {

    @FXML private ChoiceBox<String> stageChoice;
    @FXML private ChoiceBox<String> riskChoice;
    @FXML private TextField candidateField;
    @FXML private TextField positionField;
    @FXML private TextField dueAtField;
    @FXML private TextArea nextActionArea;
    @FXML private Label statusLabel;
    @FXML private ListView<CandidateCase> caseListView;
    @FXML private Button createTodoBtn;
    @FXML private Button createCaseBtn;
    @FXML private Button updateCaseBtn;
    @FXML private Button refreshCaseBtn;
    @FXML private Button sendToChatBtn;
    @FXML private Button backBtn;

    private WorkFollowupService workFollowupService;
    private HrCaseService hrCaseService;
    private Runnable onBack;
    private Consumer<String> onSendToChat;

    @FXML
    public void initialize() {
        if (stageChoice != null) {
            stageChoice.getItems().setAll("筛选", "面试", "Offer", "入职", "已关闭");
            stageChoice.setValue("筛选");
        }
        if (riskChoice != null) {
            riskChoice.getItems().setAll("低", "中", "高");
            riskChoice.setValue("中");
        }
        if (caseListView != null) {
            caseListView.setCellFactory(v -> new CaseCell());
            caseListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    fillForm(newV);
                }
            });
        }
    }

    public void setWorkFollowupService(WorkFollowupService workFollowupService) {
        this.workFollowupService = workFollowupService;
    }

    public void setHrCaseService(HrCaseService hrCaseService) {
        this.hrCaseService = hrCaseService;
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    public void setOnSendToChat(Consumer<String> onSendToChat) {
        this.onSendToChat = onSendToChat;
    }

    public void loadCases() {
        if (hrCaseService == null || caseListView == null) {
            return;
        }
        caseListView.getItems().setAll(hrCaseService.listCases(null));
    }

    @FXML
    public void onCreateCase() {
        if (hrCaseService == null) {
            setStatus("案例服务未初始化");
            return;
        }
        String candidate = text(candidateField);
        String position = text(positionField);
        String action = text(nextActionArea);
        if (candidate.isBlank()) {
            setStatus("请先填写候选人姓名");
            return;
        }
        CandidateCase created = hrCaseService.createCase(
                candidate,
                position,
                toStage(defaultText(stageChoice, "筛选")),
                "",
                toRisk(defaultText(riskChoice, "中")),
                action,
                text(dueAtField)
        );
        if (created == null) {
            setStatus("创建案例失败，请检查输入");
            return;
        }
        if (!action.isBlank()) {
            hrCaseService.addAction(created.caseId(), "next-step", action, created.dueAt(), "medium", "hr-workbench");
        }
        loadCases();
        selectCase(created.caseId());
        setStatus("已创建案例并记录下一步动作");
    }

    @FXML
    public void onUpdateCase() {
        if (hrCaseService == null || caseListView == null) {
            setStatus("案例服务未初始化");
            return;
        }
        CandidateCase selected = caseListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("请先选择一个案例");
            return;
        }
        CandidateCase updated = hrCaseService.updateCase(
                selected.caseId(),
                toStage(defaultText(stageChoice, "筛选")),
                toRisk(defaultText(riskChoice, "中")),
                text(nextActionArea),
                text(dueAtField),
                "已关闭".equals(defaultText(stageChoice, "筛选")) ? HrCaseStatus.CLOSED : HrCaseStatus.OPEN
        );
        if (updated == null) {
            setStatus("更新失败");
            return;
        }
        loadCases();
        selectCase(updated.caseId());
        setStatus("案例已更新");
    }

    @FXML
    public void onRefreshCases() {
        loadCases();
        setStatus("案例列表已刷新");
    }

    @FXML
    public void onCreateTodo() {
        if (workFollowupService == null) {
            setStatus("待办服务未初始化");
            return;
        }
        String action = text(nextActionArea);
        if (action.isBlank()) {
            setStatus("请先填写下一步动作");
            return;
        }
        String title = "HR推进: " + defaultText(candidateField, "候选人")
                + " / " + defaultText(positionField, "岗位");
        String note = "阶段: " + defaultText(stageChoice, "筛选")
                + " | 风险: " + defaultText(riskChoice, "中")
                + "\n动作: " + action;
        workFollowupService.createTodo(title, text(dueAtField), "medium", note);
        setStatus("已创建待办并进入跟进");
    }

    @FXML
    public void onSendToChat() {
        if (onSendToChat == null) {
            setStatus("聊天入口未初始化");
            return;
        }
        String prompt = "请按 HR 执行助手模式推进此事务：\n"
                + "候选人: " + defaultText(candidateField, "未提供") + "\n"
                + "岗位: " + defaultText(positionField, "未提供") + "\n"
                + "阶段: " + defaultText(stageChoice, "筛选") + "\n"
                + "风险: " + defaultText(riskChoice, "中") + "\n"
                + "下一步动作: " + text(nextActionArea) + "\n"
                + "截止时间: " + defaultText(dueAtField, "未设置") + "\n"
                + "请输出三段式：我将帮你做什么、请确认、结果。";
        onSendToChat.accept(prompt);
        setStatus("已发送到聊天并预填指令");
    }

    @FXML
    public void onBack() {
        if (onBack != null) {
            onBack.run();
        }
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text == null ? "" : text);
        }
    }

    private void selectCase(String caseId) {
        if (caseListView == null || caseId == null) {
            return;
        }
        for (CandidateCase item : caseListView.getItems()) {
            if (item != null && caseId.equals(item.caseId())) {
                caseListView.getSelectionModel().select(item);
                break;
            }
        }
    }

    private void fillForm(CandidateCase c) {
        if (c == null) {
            return;
        }
        if (candidateField != null) {
            candidateField.setText(c.candidateName());
        }
        if (positionField != null) {
            positionField.setText(c.position());
        }
        if (dueAtField != null) {
            dueAtField.setText(c.dueAt());
        }
        if (nextActionArea != null) {
            nextActionArea.setText(c.nextAction());
        }
        if (stageChoice != null) {
            stageChoice.setValue(fromStage(c.stage()));
        }
        if (riskChoice != null) {
            riskChoice.setValue(fromRisk(c.riskLevel()));
        }
    }

    private String text(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private String text(TextArea area) {
        return area == null || area.getText() == null ? "" : area.getText().trim();
    }

    private String defaultText(TextField field, String fallback) {
        String t = text(field);
        return t.isBlank() ? fallback : t;
    }

    private String defaultText(ChoiceBox<String> field, String fallback) {
        if (field == null || field.getValue() == null || field.getValue().isBlank()) {
            return fallback;
        }
        return field.getValue().trim();
    }

    private HrCaseStage toStage(String stageZh) {
        String s = stageZh == null ? "" : stageZh.trim();
        return switch (s) {
            case "搜寻" -> HrCaseStage.SOURCING;
            case "面试" -> HrCaseStage.INTERVIEW;
            case "Offer" -> HrCaseStage.OFFER;
            case "入职" -> HrCaseStage.ONBOARDING;
            case "已关闭" -> HrCaseStage.CLOSED;
            default -> HrCaseStage.SCREENING;
        };
    }

    private String fromStage(HrCaseStage stage) {
        if (stage == null) {
            return "筛选";
        }
        return switch (stage) {
            case SOURCING -> "搜寻";
            case INTERVIEW -> "面试";
            case OFFER -> "Offer";
            case ONBOARDING -> "入职";
            case CLOSED -> "已关闭";
            default -> "筛选";
        };
    }

    private HrRiskLevel toRisk(String riskZh) {
        String s = riskZh == null ? "" : riskZh.trim().toLowerCase(Locale.ROOT);
        if ("高".equals(s)) {
            return HrRiskLevel.HIGH;
        }
        if ("低".equals(s)) {
            return HrRiskLevel.LOW;
        }
        return HrRiskLevel.MEDIUM;
    }

    private String fromRisk(HrRiskLevel riskLevel) {
        if (riskLevel == HrRiskLevel.HIGH) {
            return "高";
        }
        if (riskLevel == HrRiskLevel.LOW) {
            return "低";
        }
        return "中";
    }

    private static class CaseCell extends ListCell<CandidateCase> {
        @Override
        protected void updateItem(CandidateCase item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                return;
            }
            String title = (item.candidateName() == null || item.candidateName().isBlank())
                    ? "未命名候选人"
                    : item.candidateName();
            String pos = (item.position() == null || item.position().isBlank()) ? "未填写岗位" : item.position();
            String stage = item.stage() == null ? "SCREENING" : item.stage().name();
            String risk = item.riskLevel() == null ? "MEDIUM" : item.riskLevel().name();
            setText(title + " | " + pos + "\n阶段: " + stage + " | 风险: " + risk);
        }
    }
}

