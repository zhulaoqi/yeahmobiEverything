package com.yeahmobi.everything.workfollowup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Local persistent implementation for work follow-up todos.
 * <p>
 * Uses the same storage file as AgentScope WorkFollowupTool to keep data consistent.
 * </p>
 */
public class WorkFollowupServiceImpl implements WorkFollowupService {

    private static final Logger log = LoggerFactory.getLogger(WorkFollowupServiceImpl.class);

    private static final Map<String, WorkTodo> TODOS = new ConcurrentHashMap<>();
    private static final WorkTodoMetaStore META_STORE = new WorkTodoMetaStore();
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Path STORAGE_PATH = Path.of(
            System.getProperty("user.home"),
            ".everything-assistant",
            "work-followup-todos.tsv"
    );

    static {
        loadFromDisk();
    }

    @Override
    public synchronized WorkTodo createTodo(String title, String dueAt, String priority, String note) {
        String t = title == null ? "" : title.trim();
        if (t.isBlank()) {
            return null;
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        WorkTodo item = new WorkTodo(
                id,
                t,
                dueAt == null ? "" : dueAt.trim(),
                normalizePriority(priority),
                note == null ? "" : note.trim(),
                "todo",
                nowText(),
                "",
                ""
        );
        TODOS.put(id, item);
        persistToDisk();
        return item;
    }

    @Override
    public synchronized List<WorkTodo> listTodos(String status, String sortBy) {
        String s = status == null ? "all" : status.trim().toLowerCase(Locale.ROOT);
        String sort = sortBy == null ? "priority" : sortBy.trim().toLowerCase(Locale.ROOT);
        List<WorkTodo> items = new ArrayList<>(TODOS.values());
        Comparator<WorkTodo> cmp = switch (sort) {
            case "due", "due_asc" -> Comparator.comparing(this::safeDueAt);
            case "created", "created_desc" -> Comparator.comparing(WorkTodo::createdAt).reversed();
            case "created_asc" -> Comparator.comparing(WorkTodo::createdAt);
            case "priority", "priority_desc" -> Comparator.comparingInt(this::priorityRank);
            default -> Comparator.comparingInt(this::priorityRank);
        };
        items.sort(cmp);
        if ("all".equals(s) || s.isBlank()) {
            return items;
        }
        return items.stream()
                .filter(item -> item != null && s.equalsIgnoreCase(item.status()))
                .toList();
    }

    @Override
    public synchronized WorkTodo completeTodo(String id, String review) {
        if (id == null || id.isBlank()) {
            return null;
        }
        WorkTodo old = TODOS.get(id.trim());
        if (old == null) {
            return null;
        }
        WorkTodo done = new WorkTodo(
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
        persistToDisk();
        return done;
    }

    @Override
    public synchronized WorkTodo postponeTodo(String id, int hours) {
        if (id == null || id.isBlank()) {
            return null;
        }
        WorkTodo old = TODOS.get(id.trim());
        if (old == null) {
            return null;
        }
        int deltaHours = Math.max(1, Math.min(24 * 7, hours));
        LocalDateTime base = parseDateTime(old.dueAt());
        LocalDateTime due = base != null ? base.plusHours(deltaHours) : LocalDateTime.now().plusHours(deltaHours);
        WorkTodo updated = new WorkTodo(
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
        persistToDisk();
        return updated;
    }

    @Override
    public synchronized WorkTodo deleteTodo(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        WorkTodo removed = TODOS.remove(id.trim());
        if (removed != null) {
            persistToDisk();
        }
        return removed;
    }

    @Override
    public synchronized WorkTodo updateTodoNote(String id, String note) {
        if (id == null || id.isBlank()) {
            return null;
        }
        WorkTodo old = TODOS.get(id.trim());
        if (old == null) {
            return null;
        }
        WorkTodo updated = new WorkTodo(
                old.id(),
                old.title(),
                old.dueAt(),
                old.priority(),
                note == null ? "" : note.trim(),
                old.status(),
                old.createdAt(),
                old.completedAt(),
                old.review()
        );
        TODOS.put(updated.id(), updated);
        persistToDisk();
        return updated;
    }

    @Override
    public synchronized WorkTodoMeta getTodoMeta(String todoId) {
        return META_STORE.get(todoId);
    }

    @Override
    public synchronized WorkTodoMeta upsertTodoMeta(WorkTodoMeta meta) {
        return META_STORE.upsert(meta);
    }

    @Override
    public synchronized Map<String, WorkTodoMeta> listTodoMeta() {
        return META_STORE.all();
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
            log.debug("Could not parse datetime '{}', returning null", value);
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

    private int priorityRank(WorkTodo item) {
        String p = item == null || item.priority() == null ? "medium" : item.priority().toLowerCase(Locale.ROOT);
        return switch (p) {
            case "high" -> 0;
            case "medium" -> 1;
            case "low" -> 2;
            default -> 1;
        };
    }

    private String safeDueAt(WorkTodo item) {
        if (item == null || item.dueAt() == null || item.dueAt().isBlank()) {
            return "9999-12-31 23:59";
        }
        return item.dueAt();
    }

    private static void loadFromDisk() {
        try {
            if (!Files.exists(STORAGE_PATH)) {
                return;
            }
            List<String> lines = Files.readAllLines(STORAGE_PATH, StandardCharsets.UTF_8);
            for (String line : lines) {
                WorkTodo item = decodeLine(line);
                if (item != null && item.id() != null && !item.id().isBlank()) {
                    TODOS.put(item.id(), item);
                }
            }
        } catch (Exception ignored) {
            log.debug("Could not load todos from disk, keeping empty list: {}", ignored.getMessage());
        }
    }

    private static void persistToDisk() {
        try {
            Path parent = STORAGE_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            for (WorkTodo item : TODOS.values()) {
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
            log.debug("Could not persist todos to disk, continuing with in-memory state: {}", ignored.getMessage());
        }
    }

    private static String encodeLine(WorkTodo item) {
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

    private static WorkTodo decodeLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length < 9) {
            return null;
        }
        return new WorkTodo(
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

    static List<String> parseIds(String ids) {
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
}
