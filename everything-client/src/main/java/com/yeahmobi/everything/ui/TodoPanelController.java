package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.workfollowup.WorkFollowupService;
import com.yeahmobi.everything.workfollowup.WorkReminderEmailTestSender;
import com.yeahmobi.everything.workfollowup.WorkTodoMeta;
import com.yeahmobi.everything.workfollowup.WorkTodo;
import com.yeahmobi.everything.common.Config;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller for "我的待办" panel.
 */
public class TodoPanelController {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private ChoiceBox<FilterOption> statusChoice;
    @FXML private ChoiceBox<SortOption> sortChoice;
    @FXML private ListView<WorkTodo> todoListView;
    @FXML private Label emptyLabel;
    @FXML private Button refreshBtn;
    @FXML private TextField quickTitleField;
    @FXML private DatePicker quickDatePicker;
    @FXML private ChoiceBox<String> quickTimeChoice;
    @FXML private ChoiceBox<Integer> quickLeadChoice;

    private WorkFollowupService workFollowupService;
    private Runnable onOpenCliSchedule;
    private Runnable onOpenHrWorkbench;
    private String userEmail;
    private Map<String, WorkTodoMeta> todoMetaMap = new LinkedHashMap<>();
    private static final Pattern NOTE_MAIL_STATUS = Pattern.compile("(?i)\\[mail-status=([^\\]]+)\\]");
    private static final Pattern NOTE_MAIL_SENT_AT = Pattern.compile("(?i)\\[mail-sent-at=([^\\]]+)\\]");

    private enum FilterOption {
        ALL("全部", "all"),
        TODO("待办", "todo"),
        DONE("已完成", "done");

        private final String label;
        private final String value;

        FilterOption(String label, String value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum SortOption {
        PRIORITY("优先级", "priority"),
        DUE_AT("截止时间", "due"),
        CREATED_AT("创建时间", "created_desc");

        private final String label;
        private final String value;

        SortOption(String label, String value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @FXML
    public void initialize() {
        if (statusChoice != null) {
            statusChoice.getItems().setAll(FilterOption.values());
            statusChoice.setValue(FilterOption.ALL);
            statusChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> loadTodos());
        }
        if (sortChoice != null) {
            sortChoice.getItems().setAll(SortOption.values());
            sortChoice.setValue(SortOption.PRIORITY);
            sortChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> loadTodos());
        }
        if (todoListView != null) {
            todoListView.setCellFactory(lv -> new TodoCell());
        }
        if (quickDatePicker != null) {
            quickDatePicker.setValue(LocalDate.now());
        }
        if (quickTimeChoice != null) {
            quickTimeChoice.getItems().setAll(buildHalfHourTimeOptions());
            quickTimeChoice.setValue("09:00");
        }
        if (quickLeadChoice != null) {
            quickLeadChoice.getItems().setAll(5, 10, 30);
            quickLeadChoice.setValue(5);
        }
    }

    public void setWorkFollowupService(WorkFollowupService workFollowupService) {
        this.workFollowupService = workFollowupService;
    }

    public void setOnOpenCliSchedule(Runnable onOpenCliSchedule) {
        this.onOpenCliSchedule = onOpenCliSchedule;
    }

    public void setOnOpenHrWorkbench(Runnable onOpenHrWorkbench) {
        this.onOpenHrWorkbench = onOpenHrWorkbench;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    @FXML
    public void onRefresh() {
        loadTodos();
    }

    @FXML
    public void onOpenCliSchedule() {
        if (onOpenCliSchedule != null) {
            onOpenCliSchedule.run();
        }
    }

    @FXML
    public void onOpenHrWorkbench() {
        if (onOpenHrWorkbench != null) {
            onOpenHrWorkbench.run();
        }
    }

    @FXML
    public void onQuickCreate() {
        if (workFollowupService == null) {
            return;
        }
        String title = quickTitleField == null ? "" : quickTitleField.getText();
        if (title == null || title.isBlank()) {
            return;
        }
        LocalDate date = quickDatePicker != null && quickDatePicker.getValue() != null
                ? quickDatePicker.getValue()
                : LocalDate.now();
        String timeText = quickTimeChoice != null && quickTimeChoice.getValue() != null
                ? quickTimeChoice.getValue()
                : "09:00";
        LocalTime time;
        try {
            time = LocalTime.parse(timeText.trim());
        } catch (Exception ignored) {
            time = LocalTime.of(9, 0);
        }
        LocalDateTime due = LocalDateTime.of(date, time);
        int lead = quickLeadChoice != null && quickLeadChoice.getValue() != null
                ? quickLeadChoice.getValue()
                : 5;
        String note = "邮件提醒 [lead=" + lead + "]";
        workFollowupService.createTodo(title.trim(), due.format(DATETIME_FMT), "medium", note);
        if (quickTitleField != null) {
            quickTitleField.clear();
        }
        loadTodos();
    }

    public void loadTodos() {
        if (workFollowupService == null || todoListView == null) {
            return;
        }
        String status = statusChoice != null && statusChoice.getValue() != null
                ? statusChoice.getValue().value
                : "all";
        String sortBy = sortChoice != null && sortChoice.getValue() != null
                ? sortChoice.getValue().value
                : "priority";
        List<WorkTodo> todos = workFollowupService.listTodos(status, sortBy);
        todoMetaMap = workFollowupService.listTodoMeta();
        todoListView.getItems().setAll(todos);
        boolean empty = todos == null || todos.isEmpty();
        if (emptyLabel != null) {
            emptyLabel.setManaged(empty);
            emptyLabel.setVisible(empty);
        }
    }

    private List<String> buildHalfHourTimeOptions() {
        java.util.ArrayList<String> options = new java.util.ArrayList<>();
        for (int h = 0; h < 24; h++) {
            options.add(String.format("%02d:00", h));
            options.add(String.format("%02d:30", h));
        }
        return options;
    }

    private class TodoCell extends ListCell<WorkTodo> {
        @Override
        protected void updateItem(WorkTodo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label titleLabel = new Label(item.title() == null ? "" : item.title());
            titleLabel.getStyleClass().add("card-title");

            Label metaLabel = new Label(buildMeta(item));
            metaLabel.getStyleClass().add("section-subtitle");

            Label noteLabel = new Label(item.note() == null ? "" : item.note());
            noteLabel.getStyleClass().add("card-description");
            noteLabel.setWrapText(true);
            noteLabel.setManaged(item.note() != null && !item.note().isBlank());
            noteLabel.setVisible(item.note() != null && !item.note().isBlank());

            Button doneBtn = new Button("完成");
            doneBtn.getStyleClass().add("attachment-summary-action-btn");
            doneBtn.setDisable("done".equalsIgnoreCase(item.status()));
            doneBtn.setOnAction(e -> {
                workFollowupService.completeTodo(item.id(), "");
                loadTodos();
            });

            Button postponeBtn = new Button("延后1天");
            postponeBtn.getStyleClass().add("attachment-summary-action-btn");
            postponeBtn.setOnAction(e -> {
                workFollowupService.postponeTodo(item.id(), 24);
                loadTodos();
            });

            Button deleteBtn = new Button("删除");
            deleteBtn.getStyleClass().add("attachment-summary-action-btn");
            deleteBtn.setOnAction(e -> {
                workFollowupService.deleteTodo(item.id());
                loadTodos();
            });

            Button testMailBtn = new Button("提醒测试");
            testMailBtn.getStyleClass().add("attachment-summary-action-btn");
            testMailBtn.setDisable("done".equalsIgnoreCase(item.status()));
            testMailBtn.setOnAction(e -> {
                int lead = extractLeadMinutes(item.note());
                WorkTodoMeta meta = todoMetaMap == null ? null : todoMetaMap.get(item.id());
                String channelsCsv = meta != null && meta.channelsCsv() != null && !meta.channelsCsv().isBlank()
                        ? meta.channelsCsv()
                        : "email,feishu";
                WorkReminderEmailTestSender.SendResult result = WorkReminderEmailTestSender.sendTest(
                        Config.getInstance(),
                        userEmail,
                        item,
                        lead,
                        channelsCsv
                );
                if (result.success()) {
                    String nextNote = upsertMeta(item.note(), "mail-status", "sent");
                    nextNote = upsertMeta(nextNote, "mail-sent-at", LocalDateTime.now().format(DATETIME_FMT));
                    workFollowupService.updateTodoNote(item.id(), nextNote);
                } else {
                    String nextNote = upsertMeta(item.note(), "mail-status", "failed");
                    nextNote = upsertMeta(nextNote, "mail-last-error",
                            result.message() == null ? "" : result.message().replaceAll("\\s+", " ").trim());
                    workFollowupService.updateTodoNote(item.id(), nextNote);
                }
                loadTodos();
            });

            HBox actions = new HBox(6, doneBtn, postponeBtn, testMailBtn, deleteBtn);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox top = new HBox(8, titleLabel, spacer, actions);
            VBox box = new VBox(6, top, metaLabel, noteLabel);
            box.getStyleClass().add("step-card");
            box.setStyle("-fx-padding: 10 12;");
            setText(null);
            setGraphic(box);
        }

        private String buildMeta(WorkTodo item) {
            String due = item.dueAt() == null || item.dueAt().isBlank() ? "未设置截止" : item.dueAt();
            String priority = toPriorityZh(item.priority());
            String status = "done".equalsIgnoreCase(item.status()) ? "已完成" : "待办";
            String reminder = buildReminderMeta(item);
            return "状态: " + status
                    + " | 优先级: " + priority
                    + " | 截止: " + due
                    + " | 邮件提醒: " + reminder
                    + " | 创建: " + formatCreatedAt(item.createdAt());
        }

        private String buildReminderMeta(WorkTodo item) {
            if (item == null || item.id() == null || item.id().isBlank()) {
                return "未知";
            }
            if ("done".equalsIgnoreCase(item.status())) {
                return "无需提醒";
            }
            if (item.dueAt() == null || item.dueAt().isBlank()) {
                return "未设置";
            }
            String noteStatus = extractNoteMailStatus(item.note());
            if ("sent".equals(noteStatus)) {
                String sentAt = extractNoteMailSentAt(item.note());
                return sentAt.isBlank() ? "已发送" : ("已发送(" + sentAt + ")");
            }
            if ("failed".equals(noteStatus)) {
                return "失败重试中";
            }
            WorkTodoMeta meta = todoMetaMap == null ? null : todoMetaMap.get(item.id());
            if (meta == null) {
                return "待发送";
            }
            String email = formatChannel("email", meta.emailStatus(), meta.emailLastAt());
            String feishu = formatChannel("feishu", meta.feishuStatus(), meta.feishuLastAt());
            if (email.isBlank() && feishu.isBlank()) {
                return "待发送";
            }
            return (email + " " + feishu).trim();
        }

        private String extractNoteMailStatus(String note) {
            if (note == null || note.isBlank()) {
                return "";
            }
            Matcher m = NOTE_MAIL_STATUS.matcher(note);
            if (m.find()) {
                return m.group(1) == null ? "" : m.group(1).trim().toLowerCase();
            }
            return "";
        }

        private String extractNoteMailSentAt(String note) {
            if (note == null || note.isBlank()) {
                return "";
            }
            Matcher m = NOTE_MAIL_SENT_AT.matcher(note);
            if (m.find()) {
                return m.group(1) == null ? "" : m.group(1).trim();
            }
            return "";
        }

        private int extractLeadMinutes(String note) {
            if (note == null || note.isBlank()) {
                return 5;
            }
            Matcher m = Pattern.compile("(?i)\\[lead=(\\d{1,3})\\]|提前\\s*(\\d{1,3})\\s*分钟").matcher(note);
            if (m.find()) {
                String v = m.group(1) != null ? m.group(1) : m.group(2);
                try {
                    return Math.max(1, Math.min(180, Integer.parseInt(v)));
                } catch (Exception ignored) {
                    return 5;
                }
            }
            return 5;
        }

        private String formatChannel(String name, String status, String lastAt) {
            String s = status == null ? "" : status.trim().toLowerCase();
            if (s.isBlank() || "pending".equals(s)) {
                return name + ":待发送";
            }
            if ("sent".equals(s)) {
                return name + ":" + (lastAt == null || lastAt.isBlank() ? "已发送" : "已发送@" + lastAt);
            }
            if ("failed".equals(s)) {
                return name + ":失败";
            }
            return name + ":" + s;
        }

        private String upsertMeta(String note, String key, String value) {
            String n = note == null ? "" : note.trim();
            String token = "[" + key + "=" + (value == null ? "" : value.trim()) + "]";
            String pattern = "(?i)\\[" + Pattern.quote(key) + "=[^\\]]*\\]";
            if (n.matches("(?s).*" + pattern + ".*")) {
                return n.replaceAll(pattern, Matcher.quoteReplacement(token)).trim();
            }
            if (n.isBlank()) {
                return token;
            }
            return (n + " " + token).trim();
        }

        private String formatCreatedAt(String createdAt) {
            if (createdAt == null || createdAt.isBlank()) {
                return "";
            }
            try {
                return LocalDateTime.parse(createdAt, DATETIME_FMT).format(DATETIME_FMT);
            } catch (Exception ignored) {
                try {
                    long epoch = Long.parseLong(createdAt);
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())
                            .format(DATETIME_FMT);
                } catch (Exception ignored2) {
                    return createdAt;
                }
            }
        }

        private String toPriorityZh(String p) {
            if ("high".equalsIgnoreCase(p)) {
                return "高";
            }
            if ("low".equalsIgnoreCase(p)) {
                return "低";
            }
            return "中";
        }
    }
}
