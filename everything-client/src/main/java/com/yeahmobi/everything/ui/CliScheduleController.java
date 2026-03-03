package com.yeahmobi.everything.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.cli.LocalCliGatewayClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * CLI schedule panel controller.
 */
public class CliScheduleController {

    @FXML private Button backBtn;
    @FXML private TextField nameField;
    @FXML private TextField triggerField;
    @FXML private TextField commandField;
    @FXML private Label tipLabel;
    @FXML private Label backendModeLabel;
    @FXML private ListView<JobRow> jobListView;

    private Runnable onBackCallback;
    private final LocalCliGatewayClient cliClient = new LocalCliGatewayClient();

    record JobRow(String id, String name, String command, String triggerSpec, String backend,
                  boolean enabled, String nextRunAt, String lastStatus) {
    }

    @FXML
    public void initialize() {
        if (jobListView != null) {
            jobListView.setCellFactory(v -> new JobCell());
        }
    }

    public void setOnBackCallback(Runnable onBackCallback) {
        this.onBackCallback = onBackCallback;
    }

    @FXML
    public void onBack() {
        if (onBackCallback != null) {
            onBackCallback.run();
        }
    }

    @FXML
    public void onRefresh() {
        loadJobs();
    }

    @FXML
    public void onCreate() {
        String name = nameField != null ? nameField.getText() : "";
        String trigger = triggerField != null ? triggerField.getText() : "";
        String command = commandField != null ? commandField.getText() : "";
        String resp = cliClient.scheduleCreate(name, command, trigger);
        showTip(extractMessage(resp, "创建请求已提交"));
        loadJobs();
    }

    public void loadJobs() {
        String resp = cliClient.scheduleList();
        List<JobRow> rows = parseRows(resp);
        if (jobListView != null) {
            jobListView.getItems().setAll(rows);
        }
        refreshBackendMode(rows);
    }

    private List<JobRow> parseRows(String json) {
        List<JobRow> out = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray items = root.has("items") && root.get("items").isJsonArray()
                    ? root.getAsJsonArray("items")
                    : new JsonArray();
            for (JsonElement it : items) {
                if (!it.isJsonObject()) {
                    continue;
                }
                JsonObject o = it.getAsJsonObject();
                out.add(new JobRow(
                        getStr(o, "id"),
                        getStr(o, "name"),
                        getStr(o, "command"),
                        getStr(o, "triggerSpec"),
                        getStr(o, "backend"),
                        getBool(o, "enabled"),
                        getStr(o, "nextRunAt"),
                        getStr(o, "lastStatus")
                ));
            }
        } catch (Exception ignored) {
            // keep empty list on parse error
        }
        return out;
    }

    private String extractMessage(String json, String fallback) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String m = getStr(o, "message");
            return m == null || m.isBlank() ? fallback : m;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String getStr(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) {
            return "";
        }
        try {
            return o.get(k).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean getBool(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) {
            return false;
        }
        try {
            return o.get(k).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void showTip(String text) {
        if (tipLabel != null) {
            tipLabel.setText(text == null ? "" : text);
        }
    }

    private void refreshBackendMode(List<JobRow> rows) {
        if (backendModeLabel == null) {
            return;
        }
        int nativeCount = 0;
        int internalCount = 0;
        for (JobRow row : rows) {
            if (row == null) {
                continue;
            }
            String backend = row.backend() == null ? "" : row.backend().toLowerCase();
            if (backend.startsWith("native-")) {
                nativeCount++;
            } else if ("app-internal".equals(backend)) {
                internalCount++;
            }
        }
        String os = detectOsType();
        if (nativeCount == 0 && internalCount == 0) {
            backendModeLabel.setText("当前系统：" + os + "｜调度模式：混合（优先原生，失败回退应用内）");
            return;
        }
        backendModeLabel.setText("当前系统：" + os + "｜原生任务 " + nativeCount + "｜应用内任务 " + internalCount);
    }

    private String detectOsType() {
        String resp = cliClient.detectOs();
        try {
            JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
            String os = getStr(root, "osType");
            return (os == null || os.isBlank()) ? "unknown" : os;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private class JobCell extends ListCell<JobRow> {
        @Override
        protected void updateItem(JobRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label title = new Label(item.name().isBlank() ? item.id() : item.name());
            title.getStyleClass().add("card-title");
            Label desc = new Label("触发: " + item.triggerSpec() + " | 后端: " + item.backend()
                    + " | 下次: " + item.nextRunAt() + " | 状态: " + item.lastStatus());
            desc.getStyleClass().add("section-subtitle");
            desc.setWrapText(true);
            Label cmd = new Label("命令: " + item.command());
            cmd.getStyleClass().add("card-description");
            cmd.setWrapText(true);

            Button runNow = new Button("立即执行");
            runNow.getStyleClass().add("attachment-summary-action-btn");
            runNow.setOnAction(e -> {
                showTip(extractMessage(cliClient.scheduleRunNow(item.id(), item.backend()), "已触发"));
                loadJobs();
            });
            Button pause = new Button(item.enabled() ? "暂停" : "恢复");
            pause.getStyleClass().add("attachment-summary-action-btn");
            pause.setOnAction(e -> {
                String fallback = item.enabled() ? "已暂停" : "已恢复";
                showTip(extractMessage(cliClient.schedulePause(item.id(), item.backend()), fallback));
                loadJobs();
            });
            Button del = new Button("删除");
            del.getStyleClass().add("attachment-summary-action-btn");
            del.setOnAction(e -> {
                showTip(extractMessage(cliClient.scheduleDelete(item.id(), item.backend()), "已删除"));
                loadJobs();
            });
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox actions = new HBox(6, spacer, runNow, pause, del);
            VBox box = new VBox(6, title, desc, cmd, actions);
            box.getStyleClass().add("step-card");
            box.setStyle("-fx-padding: 10 12;");
            setText(null);
            setGraphic(box);
        }
    }
}
