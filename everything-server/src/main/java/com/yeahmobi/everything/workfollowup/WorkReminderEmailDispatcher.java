package com.yeahmobi.everything.workfollowup;

import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.opsos.notify.NotifyChannelType;
import com.yeahmobi.everything.opsos.notify.NotifyDispatchResult;
import com.yeahmobi.everything.opsos.notify.NotifyHub;
import com.yeahmobi.everything.opsos.notify.NotifyMessage;
import com.yeahmobi.everything.opsos.notify.NotifyResult;
import com.yeahmobi.everything.opsos.trigger.TriggerDecision;
import com.yeahmobi.everything.opsos.trigger.TriggerEngine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified scheduler for reminder + status change + daily brief notifications.
 */
public class WorkReminderEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkReminderEmailDispatcher.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern NOTE_LEAD_PATTERN = Pattern.compile("(?i)\\[lead=(\\d{1,3})\\]|提前\\s*(\\d{1,3})\\s*分钟");
    private static final Path STATE_PATH = Path.of(
            System.getProperty("user.home"),
            ".everything-assistant",
            "work-followup-notify-state.tsv"
    );

    private final WorkFollowupService workFollowupService;
    private final Config config;
    private final String loginEmail;
    private final int defaultLeadMinutes;
    private final int pollSeconds;
    private final List<NotifyChannelType> defaultChannels;
    private final NotifyHub notifyHub;
    private final TriggerEngine triggerEngine;
    private final ScheduledExecutorService executor;
    private final Map<String, NotifyState> states = new ConcurrentHashMap<>();
    private final boolean dailyBriefEnabled;
    private final LocalTime dailyBriefTime;

    private volatile boolean started = false;

    public WorkReminderEmailDispatcher(WorkFollowupService workFollowupService, Config config, String loginEmail) {
        this.workFollowupService = workFollowupService;
        this.config = config;
        this.loginEmail = loginEmail == null ? "" : loginEmail.trim();
        this.defaultLeadMinutes = Math.max(1, config.getInt("workfollowup.reminder.default.lead.minutes", 5));
        this.pollSeconds = Math.max(10, config.getInt("workfollowup.reminder.poll.seconds", 30));
        this.defaultChannels = parseChannels(config.get("workfollowup.notify.channels", "email,feishu"));
        this.notifyHub = NotifyHub.fromConfig(config);
        this.triggerEngine = new TriggerEngine();
        this.dailyBriefEnabled = "true".equalsIgnoreCase(config.get("workfollowup.dailybrief.enabled", "true"));
        this.dailyBriefTime = parseTime(config.get("workfollowup.dailybrief.time", "18:00"));
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "work-ops-dispatcher");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        loadState();
        executor.scheduleAtFixedRate(this::safeTick, pollSeconds, pollSeconds, TimeUnit.SECONDS);
        started = true;
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        executor.shutdownNow();
        persistState();
    }

    private void safeTick() {
        try {
            tick();
        } catch (Exception e) {
            log.warn("work ops tick failed", e);
        }
    }

    private void tick() {
        if (workFollowupService == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<WorkTodo> allTodos = workFollowupService.listTodos("all", "due");
        processDueReminders(allTodos, now);
        processStatusChanged(allTodos, now);
        processDailyBrief(allTodos, now);
        persistState();
    }

    private void processDueReminders(List<WorkTodo> todos, LocalDateTime now) {
        for (WorkTodo todo : todos) {
            if (todo == null || todo.id() == null || todo.id().isBlank()) {
                continue;
            }
            if (!"todo".equalsIgnoreCase(safe(todo.status()))) {
                continue;
            }
            LocalDateTime dueAt = parseDateTime(todo.dueAt());
            if (dueAt == null) {
                continue;
            }
            WorkTodoMeta meta = ensureMeta(todo, now);
            int lead = meta == null ? defaultLeadMinutes : Math.max(1, meta.leadMinutes());
            List<NotifyChannelType> channels = parseChannels(meta == null ? "" : meta.channelsCsv());
            if (channels.isEmpty()) {
                channels = defaultChannels;
            }
            TriggerDecision decision = triggerEngine.timeBeforeDue(todo.id(), dueAt, lead, now);
            if (!decision.fire()) {
                continue;
            }
            NotifyMessage message = buildDueReminderMessage(todo, dueAt, dueAt.minusMinutes(lead), lead);
            notifyByChannels(channels, decision.idempotencyKey(), message, now, meta, todo);
        }
    }

    private void processStatusChanged(List<WorkTodo> todos, LocalDateTime now) {
        for (WorkTodo todo : todos) {
            if (todo == null || todo.id() == null || todo.id().isBlank()) {
                continue;
            }
            WorkTodoMeta meta = ensureMeta(todo, now);
            String oldStatus = meta == null ? "" : safe(meta.lastObservedStatus());
            String newStatus = safe(todo.status()).toLowerCase(Locale.ROOT);
            TriggerDecision decision = triggerEngine.statusChanged(todo.id(), oldStatus, newStatus);
            if (!decision.fire()) {
                continue;
            }
            List<NotifyChannelType> channels = parseChannels(meta == null ? "" : meta.channelsCsv());
            if (channels.isEmpty()) {
                channels = defaultChannels;
            }
            NotifyMessage message = buildStatusChangedMessage(todo, oldStatus, newStatus);
            notifyByChannels(channels, decision.idempotencyKey(), message, now, meta, todo);
        }
    }

    private void processDailyBrief(List<WorkTodo> todos, LocalDateTime now) {
        if (!dailyBriefEnabled || now.toLocalTime().isBefore(dailyBriefTime)) {
            return;
        }
        String targetEmail = resolveTargetEmail();
        NotifyMessage briefMessage = buildDailyBriefMessage(todos, now);
        for (NotifyChannelType channelType : defaultChannels) {
            TriggerDecision decision = triggerEngine.dailyBrief(now.toLocalDate(), channelType.name());
            String key = decision.idempotencyKey();
            NotifyState old = states.get(key);
            if (old != null && "sent".equalsIgnoreCase(old.status())) {
                continue;
            }
            if (old != null && old.nextRetryAt() != null && !old.nextRetryAt().isBlank()) {
                LocalDateTime retry = parseDateTime(old.nextRetryAt());
                if (retry != null && retry.isAfter(now)) {
                    continue;
                }
            }
            NotifyDispatchResult result = notifyHub.send(List.of(channelType), targetEmail, briefMessage);
            NotifyResult one = result.channelResults().isEmpty()
                    ? new NotifyResult(channelType, false, "发送失败")
                    : result.channelResults().get(0);
            updateStateByResult(key, one, now, old);
        }
    }

    private void notifyByChannels(List<NotifyChannelType> channels,
                                  String keySeed,
                                  NotifyMessage message,
                                  LocalDateTime now,
                                  WorkTodoMeta meta,
                                  WorkTodo todo) {
        String targetEmail = resolveTargetEmail();
        Map<NotifyChannelType, NotifyResult> byChannel = new EnumMap<>(NotifyChannelType.class);
        for (NotifyChannelType channel : channels) {
            String key = keySeed + "|channel|" + channel.name().toLowerCase(Locale.ROOT);
            NotifyState old = states.get(key);
            if (old != null && "sent".equalsIgnoreCase(old.status())) {
                continue;
            }
            if (old != null && old.nextRetryAt() != null && !old.nextRetryAt().isBlank()) {
                LocalDateTime retryAt = parseDateTime(old.nextRetryAt());
                if (retryAt != null && retryAt.isAfter(now)) {
                    continue;
                }
            }
            NotifyDispatchResult result = notifyHub.send(List.of(channel), targetEmail, message);
            NotifyResult one = result.channelResults().isEmpty()
                    ? new NotifyResult(channel, false, "发送失败")
                    : result.channelResults().get(0);
            byChannel.put(channel, one);
            updateStateByResult(key, one, now, old);
        }
        if (!byChannel.isEmpty()) {
            updateMetaByChannelResult(todo, meta, byChannel, now);
        }
    }

    private void updateStateByResult(String key, NotifyResult result, LocalDateTime now, NotifyState old) {
        if (result.success()) {
            states.put(key, new NotifyState(
                    key,
                    "sent",
                    now.format(DT),
                    now.format(DT),
                    old == null ? 1 : old.attempts() + 1,
                    "",
                    ""
            ));
        } else {
            int attempts = old == null ? 1 : old.attempts() + 1;
            LocalDateTime retryAt = now.plusMinutes(Math.min(30, 5 * attempts));
            states.put(key, new NotifyState(
                    key,
                    "failed",
                    now.format(DT),
                    old == null ? "" : old.sentAt(),
                    attempts,
                    safe(result.message()),
                    retryAt.format(DT)
            ));
        }
    }

    private WorkTodoMeta ensureMeta(WorkTodo todo, LocalDateTime now) {
        WorkTodoMeta old = workFollowupService.getTodoMeta(todo.id());
        if (old != null) {
            if (!safe(todo.status()).equalsIgnoreCase(safe(old.lastObservedStatus()))) {
                WorkTodoMeta refresh = new WorkTodoMeta(
                        old.todoId(),
                        Math.max(1, old.leadMinutes()),
                        safe(old.channelsCsv()),
                        safe(old.emailStatus()),
                        safe(old.emailLastAt()),
                        safe(old.emailLastError()),
                        safe(old.feishuStatus()),
                        safe(old.feishuLastAt()),
                        safe(old.feishuLastError()),
                        safe(todo.status()),
                        now.format(DT)
                );
                return workFollowupService.upsertTodoMeta(refresh);
            }
            return old;
        }
        int lead = resolveLeadMinutes(todo.note());
        String channelsCsv = toCsv(defaultChannels);
        WorkTodoMeta meta = new WorkTodoMeta(
                todo.id(),
                lead,
                channelsCsv,
                "pending",
                "",
                "",
                "pending",
                "",
                "",
                safe(todo.status()),
                now.format(DT)
        );
        return workFollowupService.upsertTodoMeta(meta);
    }

    private void updateMetaByChannelResult(WorkTodo todo,
                                           WorkTodoMeta oldMeta,
                                           Map<NotifyChannelType, NotifyResult> byChannel,
                                           LocalDateTime now) {
        WorkTodoMeta base = oldMeta == null ? ensureMeta(todo, now) : oldMeta;
        if (base == null) {
            return;
        }
        String emailStatus = base.emailStatus();
        String emailAt = base.emailLastAt();
        String emailErr = base.emailLastError();
        String feishuStatus = base.feishuStatus();
        String feishuAt = base.feishuLastAt();
        String feishuErr = base.feishuLastError();

        NotifyResult emailResult = byChannel.get(NotifyChannelType.EMAIL);
        if (emailResult != null) {
            emailStatus = emailResult.success() ? "sent" : "failed";
            emailAt = emailResult.success() ? now.format(DT) : emailAt;
            emailErr = emailResult.success() ? "" : compact(emailResult.message(), 120);
        }
        NotifyResult feishuResult = byChannel.get(NotifyChannelType.FEISHU);
        if (feishuResult != null) {
            feishuStatus = feishuResult.success() ? "sent" : "failed";
            feishuAt = feishuResult.success() ? now.format(DT) : feishuAt;
            feishuErr = feishuResult.success() ? "" : compact(feishuResult.message(), 120);
        }
        WorkTodoMeta next = new WorkTodoMeta(
                base.todoId(),
                base.leadMinutes(),
                base.channelsCsv(),
                emailStatus,
                emailAt,
                emailErr,
                feishuStatus,
                feishuAt,
                feishuErr,
                safe(todo.status()),
                now.format(DT)
        );
        workFollowupService.upsertTodoMeta(next);
    }

    private NotifyMessage buildDueReminderMessage(WorkTodo todo, LocalDateTime dueAt, LocalDateTime remindAt, int leadMinutes) {
        String title = "待办提醒：" + safe(todo.title());
        String summary = "任务即将到期，建议立即处理";
        String detail = "任务：" + safe(todo.title())
                + "\n截止：" + dueAt.format(DT)
                + "\n提醒时间：" + remindAt.format(DT) + "（提前 " + leadMinutes + " 分钟）"
                + "\n优先级：" + safe(todo.priority());
        String html = "<html><body><h3>" + escapeHtml(title) + "</h3><pre>" + escapeHtml(detail) + "</pre></body></html>";
        return new NotifyMessage("todo-reminder", title, summary, detail, html);
    }

    private NotifyMessage buildStatusChangedMessage(WorkTodo todo, String oldStatus, String newStatus) {
        String title = "任务状态更新：" + safe(todo.title());
        String summary = "任务状态发生变化";
        String detail = "任务：" + safe(todo.title())
                + "\n状态：" + (oldStatus == null || oldStatus.isBlank() ? "unknown" : oldStatus)
                + " -> " + (newStatus == null || newStatus.isBlank() ? "unknown" : newStatus)
                + "\n截止：" + safe(todo.dueAt());
        return new NotifyMessage(
                "todo-status-changed",
                title,
                summary,
                detail,
                "<html><body><h3>" + escapeHtml(title) + "</h3><pre>" + escapeHtml(detail) + "</pre></body></html>"
        );
    }

    private NotifyMessage buildDailyBriefMessage(List<WorkTodo> todos, LocalDateTime now) {
        int total = 0;
        int todoCount = 0;
        int doneCount = 0;
        int overdue = 0;
        int high = 0;
        for (WorkTodo t : todos) {
            if (t == null) {
                continue;
            }
            total++;
            if ("done".equalsIgnoreCase(safe(t.status()))) {
                doneCount++;
            } else {
                todoCount++;
            }
            if ("high".equalsIgnoreCase(safe(t.priority()))) {
                high++;
            }
            LocalDateTime due = parseDateTime(t.dueAt());
            if (due != null && due.isBefore(now) && !"done".equalsIgnoreCase(safe(t.status()))) {
                overdue++;
            }
        }
        String title = "每日执行简报（" + now.toLocalDate() + "）";
        String summary = "待办 " + todoCount + " 项，逾期 " + overdue + " 项，高优先级 " + high + " 项";
        String detail = "总任务：" + total
                + "\n待办：" + todoCount
                + "\n已完成：" + doneCount
                + "\n逾期：" + overdue
                + "\n高优先级：" + high
                + "\n建议：先处理 1-2 个高优先级且即将到期事项。";
        String html = "<html><body><h3>" + escapeHtml(title) + "</h3><pre>" + escapeHtml(detail) + "</pre></body></html>";
        return new NotifyMessage("daily-brief", title, summary, detail, html);
    }

    private int resolveLeadMinutes(String note) {
        if (note == null || note.isBlank()) {
            return defaultLeadMinutes;
        }
        Matcher m = NOTE_LEAD_PATTERN.matcher(note);
        if (m.find()) {
            String value = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                return Math.max(1, Math.min(180, Integer.parseInt(value)));
            } catch (Exception ignored) {
                log.debug("Could not parse lead minutes from note, using default {}", defaultLeadMinutes);
                return defaultLeadMinutes;
            }
        }
        return defaultLeadMinutes;
    }

    private List<NotifyChannelType> parseChannels(String csv) {
        String text = csv == null ? "" : csv.trim().toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return List.of();
        }
        List<NotifyChannelType> out = new ArrayList<>();
        for (String part : text.split("[,，\\s]+")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if ("email".equals(part) && !out.contains(NotifyChannelType.EMAIL)) {
                out.add(NotifyChannelType.EMAIL);
            } else if ("feishu".equals(part) && !out.contains(NotifyChannelType.FEISHU)) {
                out.add(NotifyChannelType.FEISHU);
            }
        }
        return out;
    }

    private String toCsv(List<NotifyChannelType> channels) {
        if (channels == null || channels.isEmpty()) {
            return "";
        }
        List<String> out = new ArrayList<>();
        for (NotifyChannelType c : channels) {
            if (c != null) {
                out.add(c.name().toLowerCase(Locale.ROOT));
            }
        }
        return String.join(",", out);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DT);
        } catch (Exception ignored) {
            log.debug("Could not parse datetime '{}', returning null", value);
            return null;
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalTime.of(18, 0);
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (Exception ignored) {
            log.debug("Could not parse time '{}', defaulting to 18:00", value);
            return LocalTime.of(18, 0);
        }
    }

    private String resolveTargetEmail() {
        String fromConfig = config.get("workfollowup.reminder.email.to", "");
        if (fromConfig != null && !fromConfig.isBlank()) {
            return fromConfig.trim();
        }
        return loginEmail;
    }

    private String compact(String text, int maxLen) {
        String t = safe(text).replaceAll("\\s+", " ").trim();
        if (t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen);
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void loadState() {
        try {
            if (!Files.exists(STATE_PATH)) {
                return;
            }
            for (String line : Files.readAllLines(STATE_PATH, StandardCharsets.UTF_8)) {
                NotifyState state = decodeState(line);
                if (state != null && state.key() != null && !state.key().isBlank()) {
                    states.put(state.key(), state);
                }
            }
        } catch (Exception e) {
            log.debug("load notify state failed", e);
        }
    }

    private void persistState() {
        try {
            Path parent = STATE_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            for (NotifyState state : states.values()) {
                lines.add(encodeState(state));
            }
            Files.write(
                    STATE_PATH,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (Exception e) {
            log.debug("persist notify state failed", e);
        }
    }

    private String encodeState(NotifyState state) {
        if (state == null) {
            return "";
        }
        return String.join("\t",
                esc(state.key()),
                esc(state.status()),
                esc(state.lastTryAt()),
                esc(state.sentAt()),
                String.valueOf(Math.max(0, state.attempts())),
                esc(state.lastError()),
                esc(state.nextRetryAt())
        );
    }

    private NotifyState decodeState(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] p = line.split("\t", -1);
        if (p.length < 7) {
            return null;
        }
        int attempts = 0;
        try {
            attempts = Integer.parseInt(unesc(p[4]));
        } catch (Exception ignored) {
            log.debug("Could not parse attempts value, defaulting to 0");
            attempts = 0;
        }
        return new NotifyState(
                unesc(p[0]),
                unesc(p[1]),
                unesc(p[2]),
                unesc(p[3]),
                attempts,
                unesc(p[5]),
                unesc(p[6])
        );
    }

    private String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String unesc(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaping) {
                switch (ch) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case '\\' -> out.append('\\');
                    default -> out.append(ch);
                }
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else {
                out.append(ch);
            }
        }
        if (escaping) {
            out.append('\\');
        }
        return out.toString();
    }

    private record NotifyState(String key,
                               String status,
                               String lastTryAt,
                               String sentAt,
                               int attempts,
                               String lastError,
                               String nextRetryAt) {
    }
}
