package com.yeahmobi.everything.machineops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Native scheduler adapter (best-effort). Unsupported cases should fallback.
 */
public class NativeOsSchedulerAdapter implements OsSchedulerAdapter {

    private static final Logger log = LoggerFactory.getLogger(NativeOsSchedulerAdapter.class);

    private static final String OS_MACOS = "macos";
    private static final String OS_WINDOWS = "windows";
    private static final String OS_LINUX = "linux";

    private final DefaultOsCommandAdapter commandAdapter = new DefaultOsCommandAdapter();
    private static final Path MAC_LAUNCHD_DIR = Path.of(
            System.getProperty("user.home"),
            ".everything-assistant",
            "launchd"
    );

    @Override
    public CliScheduleResult createJob(String name, String command, String triggerSpec) {
        String os = detectOs();
        String safeName = (name == null || name.isBlank())
                ? "EverythingCli-" + UUID.randomUUID().toString().substring(0, 8)
                : name.trim();
        if (OS_WINDOWS.equals(os)) {
            return createWindowsJob(safeName, command, triggerSpec);
        }
        if (OS_MACOS.equals(os)) {
            return createMacJob(safeName, command, triggerSpec);
        }
        if (OS_LINUX.equals(os)) {
            return createLinuxJob(safeName, command, triggerSpec);
        }
        return new CliScheduleResult(false, "native-" + os, "未知系统，建议回退应用内调度", null);
    }

    @Override
    public List<CliScheduleJob> listJobs() {
        String os = detectOs();
        if (OS_MACOS.equals(os)) {
            return listMacJobs();
        }
        if (OS_LINUX.equals(os)) {
            return listLinuxJobs();
        }
        return List.of(); // windows list kept minimal in MVP
    }

    @Override
    public CliScheduleResult pauseJob(String jobId) {
        String os = detectOs();
        if (OS_WINDOWS.equals(os) && jobId != null && !jobId.isBlank()) {
            CliCommandRequest req = new CliCommandRequest("schtasks /Change /TN \"" + jobId + "\" /Disable",
                    "", 30, false, true, null);
            CliCommandResult r = commandAdapter.execute(req,
                    new CommandPolicyDecision(true, CommandRiskLevel.LOW, false, "scheduler pause"));
            return new CliScheduleResult(r.success(), "native-windows",
                    r.success() ? "原生任务已禁用" : "原生任务禁用失败: " + r.stderr(), null);
        }
        if (OS_MACOS.equals(os) && jobId != null && !jobId.isBlank()) {
            Path plist = macPlistPath(jobId);
            CliCommandResult r = runSystem("launchctl unload -w \"" + plist + "\"", 30);
            return new CliScheduleResult(r.success(), "native-macos",
                    r.success() ? "原生任务已暂停" : "原生任务暂停失败: " + r.stderr(), null);
        }
        if (OS_LINUX.equals(os) && jobId != null && !jobId.isBlank()) {
            boolean currentlyPaused = isLinuxJobPaused(jobId);
            CliCommandResult r = runSystem(rewriteLinuxCrontab(jobId, currentlyPaused ? "resume" : "pause"), 30);
            return new CliScheduleResult(r.success(), "native-linux",
                    r.success()
                            ? (currentlyPaused ? "原生任务已恢复" : "原生任务已暂停")
                            : "原生任务状态更新失败: " + r.stderr(),
                    null);
        }
        return new CliScheduleResult(false, "native-" + os, "不支持的系统或任务 ID 为空", null);
    }

    @Override
    public CliScheduleResult deleteJob(String jobId) {
        String os = detectOs();
        if (OS_WINDOWS.equals(os) && jobId != null && !jobId.isBlank()) {
            CliCommandRequest req = new CliCommandRequest("schtasks /Delete /TN \"" + jobId + "\" /F",
                    "", 30, false, true, null);
            CliCommandResult r = commandAdapter.execute(req,
                    new CommandPolicyDecision(true, CommandRiskLevel.LOW, false, "scheduler delete"));
            return new CliScheduleResult(r.success(), "native-windows",
                    r.success() ? "原生任务已删除" : "原生任务删除失败: " + r.stderr(), null);
        }
        if (OS_MACOS.equals(os) && jobId != null && !jobId.isBlank()) {
            Path plist = macPlistPath(jobId);
            runSystem("launchctl unload -w \"" + plist + "\"", 30);
            try {
                Files.deleteIfExists(plist);
            } catch (IOException e) {
                log.warn("Failed to delete temp file, continuing: {}", e.getMessage());
            }
            return new CliScheduleResult(true, "native-macos", "原生任务已删除", null);
        }
        if (OS_LINUX.equals(os) && jobId != null && !jobId.isBlank()) {
            CliCommandResult r = runSystem(rewriteLinuxCrontab(jobId, "delete"), 30);
            return new CliScheduleResult(r.success(), "native-linux",
                    r.success() ? "原生任务已删除" : "原生任务删除失败: " + r.stderr(), null);
        }
        return new CliScheduleResult(false, "native-" + os, "不支持的系统或任务 ID 为空", null);
    }

    @Override
    public CliScheduleResult runNow(String jobId) {
        String os = detectOs();
        if (OS_WINDOWS.equals(os) && jobId != null && !jobId.isBlank()) {
            CliCommandRequest req = new CliCommandRequest("schtasks /Run /TN \"" + jobId + "\"",
                    "", 30, false, true, null);
            CliCommandResult r = commandAdapter.execute(req,
                    new CommandPolicyDecision(true, CommandRiskLevel.LOW, false, "scheduler run"));
            return new CliScheduleResult(r.success(), "native-windows",
                    r.success() ? "原生任务已触发" : "原生任务触发失败: " + r.stderr(), null);
        }
        if (OS_MACOS.equals(os) && jobId != null && !jobId.isBlank()) {
            String label = toMacLabel(jobId);
            CliCommandResult r = runSystem("launchctl start \"" + label + "\"", 30);
            return new CliScheduleResult(r.success(), "native-macos",
                    r.success() ? "原生任务已触发" : "原生任务触发失败: " + r.stderr(), null);
        }
        if (OS_LINUX.equals(os) && jobId != null && !jobId.isBlank()) {
            CliScheduleJob job = findLinuxJobById(jobId);
            if (job == null || job.command() == null || job.command().isBlank()) {
                return new CliScheduleResult(false, "native-linux", "未找到任务命令", null);
            }
            CliCommandResult r = runSystem(job.command(), 60);
            return new CliScheduleResult(r.success(), "native-linux",
                    r.success() ? "原生任务已触发" : "原生任务触发失败: " + r.stderr(), null);
        }
        return new CliScheduleResult(false, "native-" + os, "不支持的系统或任务 ID 为空", null);
    }

    private CliScheduleResult createWindowsJob(String name, String command, String triggerSpec) {
        if (command == null || command.isBlank()) {
            return new CliScheduleResult(false, "native-windows", "command 不能为空", null);
        }
        String spec = triggerSpec == null ? "" : triggerSpec.trim().toLowerCase(Locale.ROOT);
        String createCmd;
        if (spec.startsWith("every:") && spec.endsWith("m")) {
            String n = spec.substring("every:".length(), spec.length() - 1).trim();
            int mins = 30;
            try {
                mins = Math.max(1, Math.min(1440, Integer.parseInt(n)));
            } catch (Exception ignored) {
                log.debug("Could not parse interval from triggerSpec '{}', using default 30 minutes", spec);
            }
            createCmd = "schtasks /Create /TN \"" + name + "\" /TR \"" + escapeWindowsCommand(command)
                    + "\" /SC MINUTE /MO " + mins + " /F";
        } else {
            // fallback to start soon once
            createCmd = "schtasks /Create /TN \"" + name + "\" /TR \"" + escapeWindowsCommand(command)
                    + "\" /SC MINUTE /MO 30 /F";
        }
        CliCommandRequest req = new CliCommandRequest(createCmd, "", 40, false, true, null);
        CliCommandResult r = commandAdapter.execute(req,
                new CommandPolicyDecision(true, CommandRiskLevel.MEDIUM, true, "scheduler create"));
        if (!r.success()) {
            return new CliScheduleResult(false, "native-windows", "原生调度创建失败: " + r.stderr(), null);
        }
        CliScheduleJob job = new CliScheduleJob(name, name, command, triggerSpec, true,
                "native-windows", "", "", "created");
        return new CliScheduleResult(true, "native-windows", "原生调度创建成功", job);
    }

    private CliScheduleResult createMacJob(String name, String command, String triggerSpec) {
        if (command == null || command.isBlank()) {
            return new CliScheduleResult(false, "native-macos", "command 不能为空", null);
        }
        int intervalSec = parseIntervalSeconds(triggerSpec);
        String id = name.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        String label = toMacLabel(id);
        Path plist = macPlistPath(id);
        try {
            Files.createDirectories(MAC_LAUNCHD_DIR);
            String plistContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                    <plist version="1.0">
                      <dict>
                        <key>Label</key><string>%s</string>
                        <key>ProgramArguments</key>
                        <array>
                          <string>/bin/zsh</string>
                          <string>-lc</string>
                          <string>%s</string>
                        </array>
                        <key>StartInterval</key><integer>%d</integer>
                        <key>RunAtLoad</key><true/>
                      </dict>
                    </plist>
                    """.formatted(label, escapeXml(command), intervalSec);
            Files.writeString(plist, plistContent, StandardCharsets.UTF_8);
            CliCommandResult load = runSystem("launchctl load -w \"" + plist + "\"", 30);
            if (!load.success()) {
                return new CliScheduleResult(false, "native-macos", "launchd 任务加载失败: " + load.stderr(), null);
            }
            CliScheduleJob job = new CliScheduleJob(id, name, command, triggerSpec, true,
                    "native-macos", "", "", "created");
            return new CliScheduleResult(true, "native-macos", "原生调度创建成功", job);
        } catch (Exception e) {
            return new CliScheduleResult(false, "native-macos", "创建 launchd 任务失败: " + e.getMessage(), null);
        }
    }

    private CliScheduleResult createLinuxJob(String name, String command, String triggerSpec) {
        if (command == null || command.isBlank()) {
            return new CliScheduleResult(false, "native-linux", "command 不能为空", null);
        }
        String id = name.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        String cronExpr = toCronExpr(triggerSpec);
        String marker = "# YEAHMOBI_CLI:" + id;
        String line = cronExpr + " " + command + " " + marker;
        String cmd = "(crontab -l 2>/dev/null; echo \"" + escapeDoubleQuote(line) + "\") | crontab -";
        CliCommandResult r = runSystem(cmd, 30);
        if (!r.success()) {
            return new CliScheduleResult(false, "native-linux", "crontab 创建失败: " + r.stderr(), null);
        }
        CliScheduleJob job = new CliScheduleJob(id, name, command, triggerSpec, true,
                "native-linux", "", "", "created");
        return new CliScheduleResult(true, "native-linux", "原生调度创建成功", job);
    }

    private List<CliScheduleJob> listMacJobs() {
        try {
            if (!Files.exists(MAC_LAUNCHD_DIR)) {
                return List.of();
            }
            List<CliScheduleJob> out = new ArrayList<>();
            for (Path p : Files.list(MAC_LAUNCHD_DIR).toList()) {
                if (!p.getFileName().toString().endsWith(".plist")) {
                    continue;
                }
                String id = p.getFileName().toString().replace(".plist", "");
                out.add(new CliScheduleJob(id, id, "", "every:30m", true,
                        "native-macos", "", "", "unknown"));
            }
            return out;
        } catch (Exception e) {
            log.warn("Could not list macOS launchd jobs, returning empty: {}", e.getMessage());
            return List.of();
        }
    }

    private List<CliScheduleJob> listLinuxJobs() {
        CliCommandResult r = runSystem("crontab -l 2>/dev/null", 20);
        if (!r.success() || r.stdout() == null || r.stdout().isBlank()) {
            return List.of();
        }
        List<CliScheduleJob> out = new ArrayList<>();
        for (String line : r.stdout().split("\\R")) {
            String t = line == null ? "" : line.trim();
            if (!t.contains("# YEAHMOBI_CLI:")) {
                continue;
            }
            String id = t.substring(t.indexOf("# YEAHMOBI_CLI:") + "# YEAHMOBI_CLI:".length()).trim();
            String withoutMarker = t.substring(0, t.indexOf("# YEAHMOBI_CLI:")).trim();
            out.add(new CliScheduleJob(
                    id,
                    id,
                    extractLinuxCronCommand(withoutMarker),
                    extractLinuxCronExpr(withoutMarker),
                    true,
                    "native-linux",
                    "",
                    "",
                    "unknown"
            ));
        }
        return out;
    }

    private CliScheduleJob findLinuxJobById(String id) {
        return listLinuxJobs().stream()
                .filter(j -> j != null && id.equals(j.id()))
                .findFirst()
                .orElse(null);
    }

    private String rewriteLinuxCrontab(String jobId, String mode) {
        String marker = "# YEAHMOBI_CLI:" + jobId;
        String safeMode = mode == null ? "pause" : mode.trim().toLowerCase(Locale.ROOT);
        return """
                tmp=$(mktemp) && crontab -l 2>/dev/null > "$tmp" || true;
                if [ "%s" = "pause" ]; then
                  sed '/%s/s/^/#DISABLED# /' "$tmp" | crontab -;
                elif [ "%s" = "resume" ]; then
                  sed '/%s/s/^#DISABLED# //' "$tmp" | crontab -;
                else
                  sed '/%s/d' "$tmp" | crontab -;
                fi;
                rm -f "$tmp"
                """.formatted(safeMode, marker, safeMode, marker, marker).replace("\n", " ");
    }

    private CliCommandResult runSystem(String command, int timeoutSec) {
        CliCommandRequest req = new CliCommandRequest(command, "", timeoutSec, false, true, null);
        return commandAdapter.execute(req, new CommandPolicyDecision(true, CommandRiskLevel.LOW, false, "native scheduler"));
    }

    private boolean isLinuxJobPaused(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return false;
        }
        CliCommandResult r = runSystem("crontab -l 2>/dev/null", 15);
        if (!r.success() || r.stdout() == null || r.stdout().isBlank()) {
            return false;
        }
        String enabled = "# YEAHMOBI_CLI:" + jobId;
        String disabled = "#DISABLED# " + enabled;
        for (String line : r.stdout().split("\\R")) {
            String t = line == null ? "" : line.trim();
            if (t.contains(disabled)) {
                return true;
            }
            if (t.contains(enabled)) {
                return false;
            }
        }
        return false;
    }

    private int parseIntervalSeconds(String triggerSpec) {
        String spec = triggerSpec == null ? "" : triggerSpec.trim().toLowerCase(Locale.ROOT);
        if (spec.startsWith("every:") && spec.endsWith("m")) {
            String n = spec.substring("every:".length(), spec.length() - 1).trim();
            try {
                int mins = Math.max(1, Math.min(24 * 60, Integer.parseInt(n)));
                return mins * 60;
            } catch (Exception ignored) {
                log.debug("Could not parse interval seconds from '{}', using default 1800s", spec);
                return 1800;
            }
        }
        return 1800;
    }

    private String toCronExpr(String triggerSpec) {
        String spec = triggerSpec == null ? "" : triggerSpec.trim().toLowerCase(Locale.ROOT);
        if (spec.startsWith("every:") && spec.endsWith("m")) {
            String n = spec.substring("every:".length(), spec.length() - 1).trim();
            try {
                int mins = Math.max(1, Math.min(59, Integer.parseInt(n)));
                return "*/" + mins + " * * * *";
            } catch (Exception ignored) {
                log.debug("Could not parse cron expression from '{}', using default '*/30 * * * *'", spec);
                return "*/30 * * * *";
            }
        }
        return "*/30 * * * *";
    }

    private String toMacLabel(String id) {
        return "com.yeahmobi.everything.cli." + id;
    }

    private Path macPlistPath(String id) {
        return MAC_LAUNCHD_DIR.resolve(id + ".plist");
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeDoubleQuote(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "\\\"");
    }

    private String extractLinuxCronExpr(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 6) {
            return "";
        }
        return String.join(" ", parts[0], parts[1], parts[2], parts[3], parts[4]);
    }

    private String extractLinuxCronCommand(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 6) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 5; i < parts.length; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(parts[i]);
        }
        return sb.toString().trim();
    }

    private String escapeWindowsCommand(String command) {
        if (command == null) {
            return "";
        }
        return command.replace("\"", "\\\"");
    }

    private String detectOs() {
        return DefaultOsCommandAdapter.detectOs();
    }
}
