package com.yeahmobi.everything.machineops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight local audit store for CLI operations.
 */
public class CliAuditStore {

    private static final Logger log = LoggerFactory.getLogger(CliAuditStore.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path STORAGE = Path.of(
            System.getProperty("user.home"),
            ".everything-assistant",
            "cli-audit.log"
    );

    public synchronized void logExec(CliCommandResult result) {
        if (result == null) {
            return;
        }
        String line = String.join("\t",
                LocalDateTime.now().format(DT),
                nullSafe(result.osType()),
                nullSafe(result.command()),
                result.success() ? "SUCCESS" : "FAILED",
                Integer.toString(result.exitCode()),
                nullSafe(result.message())
        );
        appendLine(line);
    }

    public synchronized void logSchedule(String action, CliScheduleResult result) {
        String line = String.join("\t",
                LocalDateTime.now().format(DT),
                "SCHEDULE",
                nullSafe(action),
                result != null && result.success() ? "SUCCESS" : "FAILED",
                result != null ? nullSafe(result.backend()) : "",
                result != null ? nullSafe(result.message()) : ""
        );
        appendLine(line);
    }

    private void appendLine(String line) {
        try {
            Path parent = STORAGE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(
                    STORAGE,
                    List.of(line),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
            log.debug("Could not write CLI audit entry, skipping: {}", ignored.getMessage());
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.replace("\n", "\\n").replace("\r", "\\r").replace("\t", " ");
    }
}
