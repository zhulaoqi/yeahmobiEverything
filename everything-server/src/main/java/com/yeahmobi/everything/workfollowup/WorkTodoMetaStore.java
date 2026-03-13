package com.yeahmobi.everything.workfollowup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local persistent storage for structured todo notification metadata.
 */
public class WorkTodoMetaStore {

    private static final Logger log = LoggerFactory.getLogger(WorkTodoMetaStore.class);
    private static final Path STORAGE = Path.of(
            System.getProperty("user.home"),
            ".everything-assistant",
            "work-followup-todo-meta.tsv"
    );

    private final Map<String, WorkTodoMeta> metas = new LinkedHashMap<>();

    public WorkTodoMetaStore() {
        load();
    }

    public synchronized WorkTodoMeta get(String todoId) {
        if (todoId == null || todoId.isBlank()) {
            return null;
        }
        return metas.get(todoId.trim());
    }

    public synchronized Map<String, WorkTodoMeta> all() {
        return new LinkedHashMap<>(metas);
    }

    public synchronized WorkTodoMeta upsert(WorkTodoMeta meta) {
        if (meta == null || meta.todoId() == null || meta.todoId().isBlank()) {
            return null;
        }
        String id = meta.todoId().trim();
        WorkTodoMeta fixed = new WorkTodoMeta(
                id,
                Math.max(1, meta.leadMinutes()),
                safe(meta.channelsCsv()),
                safe(meta.emailStatus()),
                safe(meta.emailLastAt()),
                safe(meta.emailLastError()),
                safe(meta.feishuStatus()),
                safe(meta.feishuLastAt()),
                safe(meta.feishuLastError()),
                safe(meta.lastObservedStatus()),
                safe(meta.updatedAt())
        );
        metas.put(id, fixed);
        persist();
        return fixed;
    }

    private void load() {
        try {
            if (!Files.exists(STORAGE)) {
                return;
            }
            for (String line : Files.readAllLines(STORAGE, StandardCharsets.UTF_8)) {
                WorkTodoMeta meta = decode(line);
                if (meta != null && meta.todoId() != null && !meta.todoId().isBlank()) {
                    metas.put(meta.todoId(), meta);
                }
            }
        } catch (Exception ignored) {
            log.debug("Could not load todo meta from disk, keeping empty: {}", ignored.getMessage());
        }
    }

    private void persist() {
        try {
            Path parent = STORAGE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            for (WorkTodoMeta meta : metas.values()) {
                lines.add(encode(meta));
            }
            Files.write(
                    STORAGE,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (Exception ignored) {
            log.debug("Could not persist todo meta to disk, continuing with in-memory state: {}", ignored.getMessage());
        }
    }

    private String encode(WorkTodoMeta meta) {
        if (meta == null) {
            return "";
        }
        return String.join("\t",
                esc(meta.todoId()),
                String.valueOf(Math.max(1, meta.leadMinutes())),
                esc(meta.channelsCsv()),
                esc(meta.emailStatus()),
                esc(meta.emailLastAt()),
                esc(meta.emailLastError()),
                esc(meta.feishuStatus()),
                esc(meta.feishuLastAt()),
                esc(meta.feishuLastError()),
                esc(meta.lastObservedStatus()),
                esc(meta.updatedAt())
        );
    }

    private WorkTodoMeta decode(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] p = line.split("\t", -1);
        if (p.length < 11) {
            return null;
        }
        int lead = 5;
        try {
            lead = Integer.parseInt(unesc(p[1]));
        } catch (Exception ignored) {
            log.debug("Could not parse lead minutes value '{}', using default 5", p[1]);
            lead = 5;
        }
        return new WorkTodoMeta(
                unesc(p[0]),
                Math.max(1, lead),
                unesc(p[2]),
                unesc(p[3]),
                unesc(p[4]),
                unesc(p[5]),
                unesc(p[6]),
                unesc(p[7]),
                unesc(p[8]),
                unesc(p[9]),
                unesc(p[10])
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
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
}
