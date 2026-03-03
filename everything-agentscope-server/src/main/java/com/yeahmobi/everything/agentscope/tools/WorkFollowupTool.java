package com.yeahmobi.everything.agentscope.tools;

import io.agentscope.core.tool.Toolkit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal work-followup toolkit for "个人工作跟进秘书".
 * Provides todo/reminder/review capabilities without external dependencies.
 */
public class WorkFollowupTool extends Toolkit {

    private static final Map<String, TodoItem> TODOS = new ConcurrentHashMap<>();
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Path STORAGE_PATH = Path.of(
            System.getProperty("user.home"),
            ".everything-assistant",
            "work-followup-todos.tsv"
    );

    static {
        loadFromDisk();
    }

    public String createTodo(String title, String dueAt, String priority, String note) {
        String t = title == null ? "" : title.trim();
        if (t.isBlank()) {
            return "title 不能为空";
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        String normalizedPriority = normalizePriority(priority);
        TodoItem item = new TodoItem(
                id,
                t,
                dueAt == null ? "" : dueAt.trim(),
                normalizedPriority,
                note == null ? "" : note.trim(),
                "todo",
                nowText(),
                "",
                ""
        );
        TODOS.put(id, item);
        persistToDisk();
        return "已创建待办\nID: " + id + "\n标题: " + t + "\n截止: "
                + (item.dueAt().isBlank() ? "未设置" : item.dueAt())
                + "\n优先级: " + item.priority();
    }

    public String listTodos(String status) {
        String s = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        List<TodoItem> items = new ArrayList<>(TODOS.values());
        items.sort(Comparator.comparing(TodoItem::createdAt).reversed());
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (TodoItem item : items) {
            if (!s.isBlank() && !item.status().equalsIgnoreCase(s)) {
                continue;
            }
            out.append("- [").append(item.status()).append("] ")
                    .append(item.title()).append(" (ID=").append(item.id()).append(")\n")
                    .append("  截止: ").append(item.dueAt().isBlank() ? "未设置" : item.dueAt())
                    .append(" | 优先级: ").append(item.priority()).append("\n");
            if (!item.note().isBlank()) {
                out.append("  备注: ").append(item.note()).append("\n");
            }
            shown++;
        }
        if (shown == 0) {
            return "暂无符合条件的待办";
        }
        return out.toString().trim();
    }

    public String listTodosSorted(String status, String sortBy) {
        String s = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        String sort = sortBy == null ? "created_desc" : sortBy.trim().toLowerCase(Locale.ROOT);
        List<TodoItem> items = new ArrayList<>(TODOS.values());
        Comparator<TodoItem> cmp;
        switch (sort) {
            case "priority", "priority_desc" -> cmp = Comparator.comparingInt(this::priorityRank);
            case "due", "due_asc" -> cmp = Comparator.comparing(this::safeDueAt);
            case "created_asc" -> cmp = Comparator.comparing(TodoItem::createdAt);
            default -> cmp = Comparator.comparing(TodoItem::createdAt).reversed();
        }
        items.sort(cmp);
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (TodoItem item : items) {
            if (!s.isBlank() && !item.status().equalsIgnoreCase(s)) {
                continue;
            }
            out.append("- [").append(item.status()).append("] ")
                    .append(item.title()).append(" (ID=").append(item.id()).append(")\n")
                    .append("  截止: ").append(item.dueAt().isBlank() ? "未设置" : item.dueAt())
                    .append(" | 优先级: ").append(item.priority()).append("\n");
            shown++;
        }
        if (shown == 0) {
            return "暂无符合条件的待办";
        }
        return out.toString().trim();
    }

    public String updateTodo(String id, String title, String dueAt, String priority, String note, String status) {
        if (id == null || id.isBlank()) {
            return "id 不能为空";
        }
        TodoItem old = TODOS.get(id.trim());
        if (old == null) {
            return "未找到待办: " + id;
        }
        String nextTitle = (title == null || title.isBlank()) ? old.title() : title.trim();
        String nextDueAt = dueAt == null ? old.dueAt() : dueAt.trim();
        String nextPriority = (priority == null || priority.isBlank()) ? old.priority() : normalizePriority(priority);
        String nextNote = note == null ? old.note() : note.trim();
        String nextStatus = (status == null || status.isBlank()) ? old.status() : normalizeStatus(status);
        TodoItem updated = new TodoItem(
                old.id(),
                nextTitle,
                nextDueAt,
                nextPriority,
                nextNote,
                nextStatus,
                old.createdAt(),
                "done".equalsIgnoreCase(nextStatus) ? nowText() : old.completedAt(),
                old.review()
        );
        TODOS.put(updated.id(), updated);
        persistToDisk();
        return "已更新待办: " + updated.id();
    }

    public String deleteTodo(String id) {
        if (id == null || id.isBlank()) {
            return "id 不能为空";
        }
        TodoItem removed = TODOS.remove(id.trim());
        if (removed == null) {
            return "未找到待办: " + id;
        }
        persistToDisk();
        return "已删除待办: " + removed.title() + " (ID=" + removed.id() + ")";
    }

    public String bulkComplete(String ids, String review) {
        List<String> parsed = parseIds(ids);
        if (parsed.isEmpty()) {
            return "请提供待办 ID（可用逗号分隔）";
        }
        int success = 0;
        List<String> missing = new ArrayList<>();
        for (String id : parsed) {
            TodoItem old = TODOS.get(id);
            if (old == null) {
                missing.add(id);
                continue;
            }
            TodoItem done = new TodoItem(
                    old.id(),
                    old.title(),
                    old.dueAt(),
                    old.priority(),
                    old.note(),
                    "done",
                    old.createdAt(),
                    nowText(),
                    review == null ? old.review() : review.trim()
            );
            TODOS.put(done.id(), done);
            success++;
        }
        if (success > 0) {
            persistToDisk();
        }
        StringBuilder sb = new StringBuilder("批量完成结果: 成功 ").append(success).append(" 条");
        if (!missing.isEmpty()) {
            sb.append("，未找到 ").append(missing.size()).append(" 条: ").append(String.join(", ", missing));
        }
        return sb.toString();
    }

    public String bulkPostpone(String ids, Integer postponeHours, String newDueAt) {
        List<String> parsed = parseIds(ids);
        if (parsed.isEmpty()) {
            return "请提供待办 ID（可用逗号分隔）";
        }
        int hours = postponeHours == null || postponeHours <= 0 ? 24 : Math.min(postponeHours, 24 * 30);
        LocalDateTime explicitDue = parseDateTime(newDueAt);
        int success = 0;
        List<String> missing = new ArrayList<>();
        for (String id : parsed) {
            TodoItem old = TODOS.get(id);
            if (old == null) {
                missing.add(id);
                continue;
            }
            LocalDateTime base = parseDateTime(old.dueAt());
            LocalDateTime due = explicitDue != null
                    ? explicitDue
                    : (base != null ? base.plusHours(hours) : LocalDateTime.now().plusHours(hours));
            TodoItem updated = new TodoItem(
                    old.id(),
                    old.title(),
                    due.format(DATE_TIME_FMT),
                    old.priority(),
                    old.note(),
                    old.status(),
                    old.createdAt(),
                    old.completedAt(),
                    old.review()
            );
            TODOS.put(updated.id(), updated);
            success++;
        }
        if (success > 0) {
            persistToDisk();
        }
        StringBuilder sb = new StringBuilder("批量延期结果: 成功 ").append(success).append(" 条");
        if (explicitDue != null) {
            sb.append("，新截止: ").append(explicitDue.format(DATE_TIME_FMT));
        } else {
            sb.append("，顺延 ").append(hours).append(" 小时");
        }
        if (!missing.isEmpty()) {
            sb.append("，未找到 ").append(missing.size()).append(" 条: ").append(String.join(", ", missing));
        }
        return sb.toString();
    }

    public String completeTodo(String id, String review) {
        if (id == null || id.isBlank()) {
            return "id 不能为空";
        }
        TodoItem old = TODOS.get(id.trim());
        if (old == null) {
            return "未找到待办: " + id;
        }
        TodoItem done = new TodoItem(
                old.id(),
                old.title(),
                old.dueAt(),
                old.priority(),
                old.note(),
                "done",
                old.createdAt(),
                nowText(),
                review == null ? "" : review.trim()
        );
        TODOS.put(done.id(), done);
        persistToDisk();
        return "已完成待办: " + done.title()
                + (done.review().isBlank() ? "" : "\n复盘: " + done.review());
    }

    public String remindDue(Integer withinHours) {
        int hours = withinHours == null || withinHours <= 0 ? 24 : Math.min(withinHours, 168);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusHours(hours);
        List<TodoItem> dueItems = new ArrayList<>();
        for (TodoItem item : TODOS.values()) {
            if (!"todo".equalsIgnoreCase(item.status())) {
                continue;
            }
            LocalDateTime due = parseDateTime(item.dueAt());
            if (due != null && (due.isEqual(now) || (due.isAfter(now) && due.isBefore(deadline)))) {
                dueItems.add(item);
            }
        }
        dueItems.sort(Comparator.comparing(TodoItem::dueAt));
        if (dueItems.isEmpty()) {
            return "未来 " + hours + " 小时内没有到期待办";
        }
        StringBuilder sb = new StringBuilder("未来 ").append(hours).append(" 小时到期提醒:\n");
        for (TodoItem item : dueItems) {
            sb.append("- ").append(item.title()).append(" (ID=").append(item.id()).append(")")
                    .append(" 截止: ").append(item.dueAt()).append("\n");
        }
        return sb.toString().trim();
    }

    public String weeklyReview() {
        int total = 0;
        int done = 0;
        int todo = 0;
        int high = 0;
        List<TodoItem> pendingHigh = new ArrayList<>();
        for (TodoItem item : TODOS.values()) {
            total++;
            if ("done".equalsIgnoreCase(item.status())) {
                done++;
            } else {
                todo++;
            }
            if ("high".equalsIgnoreCase(item.priority())) {
                high++;
                if ("todo".equalsIgnoreCase(item.status())) {
                    pendingHigh.add(item);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("工作复盘概览:\n")
                .append("- 总待办: ").append(total).append("\n")
                .append("- 已完成: ").append(done).append("\n")
                .append("- 待推进: ").append(todo).append("\n")
                .append("- 高优先级总数: ").append(high).append("\n");
        if (!pendingHigh.isEmpty()) {
            sb.append("- 本周优先推进:\n");
            for (TodoItem item : pendingHigh.stream().limit(5).toList()) {
                sb.append("  - ").append(item.title()).append(" (ID=").append(item.id()).append(")\n");
            }
        }
        sb.append("- 建议下一步: 先处理 1-2 个高优先级项，并补齐截止时间。");
        return sb.toString().trim();
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FMT);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FMT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizePriority(String priority) {
        String p = priority == null ? "" : priority.trim().toLowerCase(Locale.ROOT);
        if ("high".equals(p) || "h".equals(p) || "高".equals(p)) {
            return "high";
        }
        if ("low".equals(p) || "l".equals(p) || "低".equals(p)) {
            return "low";
        }
        return "medium";
    }

    private String normalizeStatus(String status) {
        String s = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if ("done".equals(s) || "completed".equals(s) || "完成".equals(s)) {
            return "done";
        }
        if ("cancelled".equals(s) || "canceled".equals(s) || "取消".equals(s)) {
            return "cancelled";
        }
        return "todo";
    }

    private int priorityRank(TodoItem item) {
        String p = item == null || item.priority() == null ? "medium" : item.priority().toLowerCase(Locale.ROOT);
        return switch (p) {
            case "high" -> 0;
            case "medium" -> 1;
            case "low" -> 2;
            default -> 1;
        };
    }

    private String safeDueAt(TodoItem item) {
        if (item == null || item.dueAt() == null || item.dueAt().isBlank()) {
            return "9999-12-31 23:59";
        }
        return item.dueAt();
    }

    private List<String> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        String[] parts = ids.split("[,，\\s]+");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String id = part.trim();
            if (!id.isBlank()) {
                unique.add(id);
            }
        }
        return new ArrayList<>(unique);
    }

    private static void loadFromDisk() {
        try {
            if (!Files.exists(STORAGE_PATH)) {
                return;
            }
            List<String> lines = Files.readAllLines(STORAGE_PATH, StandardCharsets.UTF_8);
            for (String line : lines) {
                TodoItem item = decodeLine(line);
                if (item != null && item.id() != null && !item.id().isBlank()) {
                    TODOS.put(item.id(), item);
                }
            }
        } catch (Exception ignored) {
            // Keep in-memory fallback when storage unavailable.
        }
    }

    private static void persistToDisk() {
        try {
            Path parent = STORAGE_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            for (TodoItem item : TODOS.values()) {
                lines.add(encodeLine(item));
            }
            Files.write(
                    STORAGE_PATH,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (Exception ignored) {
            // Ignore persistence errors to keep tool callable.
        }
    }

    private static String encodeLine(TodoItem item) {
        if (item == null) {
            return "";
        }
        return String.join("\t",
                esc(item.id()),
                esc(item.title()),
                esc(item.dueAt()),
                esc(item.priority()),
                esc(item.note()),
                esc(item.status()),
                esc(item.createdAt()),
                esc(item.completedAt()),
                esc(item.review())
        );
    }

    private static TodoItem decodeLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length < 9) {
            return null;
        }
        return new TodoItem(
                unesc(parts[0]),
                unesc(parts[1]),
                unesc(parts[2]),
                unesc(parts[3]),
                unesc(parts[4]),
                unesc(parts[5]),
                unesc(parts[6]),
                unesc(parts[7]),
                unesc(parts[8])
        );
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unesc(String value) {
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
                continue;
            }
            if (ch == '\\') {
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

    private record TodoItem(String id,
                            String title,
                            String dueAt,
                            String priority,
                            String note,
                            String status,
                            String createdAt,
                            String completedAt,
                            String review) {
    }
}
