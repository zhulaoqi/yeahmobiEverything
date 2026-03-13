package com.yeahmobi.everything.workfollowup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read reminder email dispatch states for UI display.
 */
public final class WorkReminderStateReader {

    private static final Logger log = LoggerFactory.getLogger(WorkReminderStateReader.class);
    private static final Path STATE_PATH = Path.of(
            System.getProperty("user.home"),
            ".everything-assistant",
            "work-followup-reminder-email-state.tsv"
    );

    public Map<String, ReminderStateView> loadByTodoKey() {
        Map<String, ReminderStateView> out = new LinkedHashMap<>();
        try {
            if (!Files.exists(STATE_PATH)) {
                return out;
            }
            for (String line : Files.readAllLines(STATE_PATH, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String[] p = line.split("\t", -1);
                if (p.length < 7) {
                    continue;
                }
                String rawKey = unesc(p[0]);
                String status = unesc(p[1]);
                String lastTryAt = unesc(p[2]);
                String sentAt = unesc(p[3]);
                String lastError = unesc(p[5]);
                String todoKey = toTodoKey(rawKey);
                if (todoKey.isBlank()) {
                    continue;
                }
                ReminderStateView old = out.get(todoKey);
                if (old == null || compareTime(lastTryAt, old.lastTryAt()) >= 0) {
                    out.put(todoKey, new ReminderStateView(status, lastTryAt, sentAt, lastError));
                }
            }
        } catch (Exception ignored) {
            log.debug("Could not read reminder state file, UI continues without reminder state: {}", ignored.getMessage());
        }
        return out;
    }

    private String toTodoKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        String[] parts = rawKey.split("\\|", -1);
        if (parts.length < 2) {
            return "";
        }
        return parts[0] + "|" + parts[1];
    }

    private int compareTime(String a, String b) {
        String left = a == null ? "" : a;
        String right = b == null ? "" : b;
        return left.compareTo(right);
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

    public record ReminderStateView(String status, String lastTryAt, String sentAt, String lastError) {
    }
}
