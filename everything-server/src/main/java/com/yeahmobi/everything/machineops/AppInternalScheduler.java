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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Application-level scheduler fallback.
 */
public class AppInternalScheduler implements OsSchedulerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AppInternalScheduler.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Path STORAGE = Path.of(
            System.getProperty("user.home"),
            ".everything-assistant",
            "cli-schedules.tsv"
    );

    private final Map<String, CliScheduleJob> jobs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private final DefaultOsCommandAdapter commandAdapter = new DefaultOsCommandAdapter();

    public AppInternalScheduler() {
        load();
        startLoop();
    }

    @Override
    public synchronized CliScheduleResult createJob(String name, String command, String triggerSpec) {
        if (name == null || name.isBlank() || command == null || command.isBlank()) {
            return new CliScheduleResult(false, "app-internal", "name/command 不能为空", null);
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        String nextRunAt = calcNextRunAt(triggerSpec);
        CliScheduleJob job = new CliScheduleJob(
                id,
                name.trim(),
                command.trim(),
                triggerSpec == null ? "" : triggerSpec.trim(),
                true,
                "app-internal",
                nextRunAt,
                "",
                "created"
        );
        jobs.put(id, job);
        persist();
        return new CliScheduleResult(true, "app-internal", "任务已创建", job);
    }

    @Override
    public synchronized List<CliScheduleJob> listJobs() {
        return new ArrayList<>(jobs.values());
    }

    @Override
    public synchronized CliScheduleResult pauseJob(String jobId) {
        CliScheduleJob old = jobs.get(jobId);
        if (old == null) {
            return new CliScheduleResult(false, "app-internal", "任务不存在", null);
        }
        boolean nextEnabled = !old.enabled();
        CliScheduleJob updated = new CliScheduleJob(
                old.id(),
                old.name(),
                old.command(),
                old.triggerSpec(),
                nextEnabled,
                old.backend(),
                nextEnabled ? calcNextRunAt(old.triggerSpec()) : old.nextRunAt(),
                old.lastRunAt(),
                nextEnabled ? "resumed" : "paused"
        );
        jobs.put(updated.id(), updated);
        persist();
        return new CliScheduleResult(true, "app-internal", nextEnabled ? "任务已恢复" : "任务已暂停", updated);
    }

    @Override
    public synchronized CliScheduleResult deleteJob(String jobId) {
        CliScheduleJob removed = jobs.remove(jobId);
        if (removed == null) {
            return new CliScheduleResult(false, "app-internal", "任务不存在", null);
        }
        persist();
        return new CliScheduleResult(true, "app-internal", "任务已删除", removed);
    }

    @Override
    public synchronized CliScheduleResult runNow(String jobId) {
        CliScheduleJob job = jobs.get(jobId);
        if (job == null) {
            return new CliScheduleResult(false, "app-internal", "任务不存在", null);
        }
        runJob(job);
        CliScheduleJob latest = jobs.get(jobId);
        return new CliScheduleResult(true, "app-internal", "任务已执行", latest);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void startLoop() {
        executor.scheduleAtFixedRate(() -> {
            try {
                tick();
            } catch (Exception ignored) {
                log.warn("Scheduler tick threw unexpected exception, keeping loop alive: {}", ignored.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private synchronized void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (CliScheduleJob job : jobs.values()) {
            if (job == null || !job.enabled() || job.nextRunAt() == null || job.nextRunAt().isBlank()) {
                continue;
            }
            LocalDateTime due;
            try {
                due = LocalDateTime.parse(job.nextRunAt(), DT);
            } catch (Exception ignored) {
                log.debug("Could not parse nextRunAt for job '{}', skipping: {}", job.id(), ignored.getMessage());
                continue;
            }
            if (!due.isAfter(now)) {
                runJob(job);
            }
        }
    }

    private void runJob(CliScheduleJob job) {
        CliCommandRequest req = new CliCommandRequest(job.command(), "", 120, false, true, null);
        CommandPolicyDecision policy = new CommandPolicyDecision(true, CommandRiskLevel.LOW, false, "scheduler run");
        CliCommandResult result = commandAdapter.execute(req, policy);
        String now = LocalDateTime.now().format(DT);
        String nextRun = calcNextRunAt(job.triggerSpec());
        CliScheduleJob updated = new CliScheduleJob(
                job.id(),
                job.name(),
                job.command(),
                job.triggerSpec(),
                job.enabled(),
                job.backend(),
                nextRun,
                now,
                result.success() ? "success" : "failed"
        );
        jobs.put(updated.id(), updated);
        persist();
    }

    private String calcNextRunAt(String triggerSpec) {
        // Supported:
        // - every:30m
        // - once:yyyy-MM-dd HH:mm
        if (triggerSpec == null || triggerSpec.isBlank()) {
            return LocalDateTime.now().plusHours(1).format(DT);
        }
        String spec = triggerSpec.trim().toLowerCase();
        if (spec.startsWith("once:")) {
            String value = triggerSpec.substring("once:".length()).trim();
            try {
                LocalDateTime dt = LocalDateTime.parse(value, DT);
                return dt.format(DT);
            } catch (Exception ignored) {
                log.debug("Could not parse once: datetime from '{}', defaulting to +1h", triggerSpec);
                return LocalDateTime.now().plusHours(1).format(DT);
            }
        }
        if (spec.startsWith("every:") && spec.endsWith("m")) {
            String num = spec.substring("every:".length(), spec.length() - 1).trim();
            try {
                int mins = Math.max(1, Math.min(24 * 60, Integer.parseInt(num)));
                return LocalDateTime.now().plusMinutes(mins).format(DT);
            } catch (Exception ignored) {
                log.debug("Could not parse every: interval from '{}', defaulting to +30m", triggerSpec);
                return LocalDateTime.now().plusMinutes(30).format(DT);
            }
        }
        return LocalDateTime.now().plusMinutes(30).format(DT);
    }

    private synchronized void persist() {
        try {
            Path parent = STORAGE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            for (CliScheduleJob job : jobs.values()) {
                lines.add(String.join("\t",
                        esc(job.id()), esc(job.name()), esc(job.command()), esc(job.triggerSpec()),
                        job.enabled() ? "1" : "0", esc(job.backend()), esc(job.nextRunAt()),
                        esc(job.lastRunAt()), esc(job.lastStatus())
                ));
            }
            Files.write(STORAGE, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception ignored) {
            log.debug("Could not persist schedule jobs to disk, continuing with in-memory state: {}", ignored.getMessage());
        }
    }

    private synchronized void load() {
        try {
            if (!Files.exists(STORAGE)) {
                return;
            }
            for (String line : Files.readAllLines(STORAGE, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length < 9) {
                    continue;
                }
                CliScheduleJob job = new CliScheduleJob(
                        unesc(p[0]), unesc(p[1]), unesc(p[2]), unesc(p[3]),
                        "1".equals(p[4]), unesc(p[5]), unesc(p[6]), unesc(p[7]), unesc(p[8])
                );
                if (job.id() != null && !job.id().isBlank()) {
                    jobs.put(job.id(), job);
                }
            }
        } catch (Exception ignored) {
            log.debug("Could not load schedule jobs from disk, starting with empty state: {}", ignored.getMessage());
        }
    }

    private static String esc(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unesc(String v) {
        if (v == null || v.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
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
