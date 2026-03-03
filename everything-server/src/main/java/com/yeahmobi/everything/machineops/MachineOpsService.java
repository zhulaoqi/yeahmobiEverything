package com.yeahmobi.everything.machineops;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facade for command execution and scheduling.
 */
public class MachineOpsService {

    private final CommandPolicyEngine policyEngine;
    private final OsCommandAdapter commandAdapter;
    private final NativeOsSchedulerAdapter nativeScheduler;
    private final AppInternalScheduler fallbackScheduler;
    private final CliAuditStore auditStore;
    private final Map<String, ConfirmTicket> confirmTickets = new ConcurrentHashMap<>();

    public MachineOpsService() {
        this.policyEngine = new CommandPolicyEngine();
        this.commandAdapter = new DefaultOsCommandAdapter();
        this.nativeScheduler = new NativeOsSchedulerAdapter();
        this.fallbackScheduler = new AppInternalScheduler();
        this.auditStore = new CliAuditStore();
    }

    public String detectOs() {
        return DefaultOsCommandAdapter.detectOs();
    }

    public CliCommandResult exec(CliCommandRequest request) {
        cleanupExpiredTickets();
        CommandPolicyDecision decision = policyEngine.evaluate(
                request != null ? request.command() : "",
                request != null ? request.workingDirectory() : ""
        );
        if (!decision.allowed()) {
            CliCommandResult blocked = new CliCommandResult(
                    false, false, decision.riskLevel(), -1, detectOs(),
                    request != null ? request.command() : "",
                    "",
                    "",
                    decision.reason()
            );
            auditStore.logExec(blocked);
            return blocked;
        }
        if (decision.requiresConfirm() && (request == null || !isTicketValid(request))) {
            CliCommandResult blocked = new CliCommandResult(
                    false,
                    false,
                    decision.riskLevel(),
                    -1,
                    detectOs(),
                    request != null ? request.command() : "",
                    "",
                    "",
                    "中风险命令需要确认票据，请先确认后再执行（30秒内有效）"
            );
            auditStore.logExec(blocked);
            return blocked;
        }
        CliCommandResult result = commandAdapter.execute(request, decision);
        if (shouldAutoRetry(request, result)) {
            CliCommandRequest retryReq = new CliCommandRequest(
                    request.command(),
                    "",
                    request.timeoutSeconds(),
                    false,
                    true,
                    request.confirmTicket()
            );
            CliCommandResult retry = commandAdapter.execute(retryReq, decision);
            CliCommandResult merged = mergeRetryResult(result, retry);
            merged = withRecoveryHint(merged);
            auditStore.logExec(result);
            auditStore.logExec(merged);
            return merged;
        }
        result = withRecoveryHint(result);
        auditStore.logExec(result);
        return result;
    }

    public String issueConfirmTicket(String command, String workingDirectory) {
        cleanupExpiredTickets();
        String cmd = command == null ? "" : command.trim();
        if (cmd.isBlank()) {
            return "";
        }
        CommandPolicyDecision decision = policyEngine.evaluate(cmd, workingDirectory == null ? "" : workingDirectory);
        if (!decision.allowed()) {
            return "";
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + 30_000L;
        confirmTickets.put(ticket, new ConfirmTicket(cmd, safe(workingDirectory), expiresAt));
        return ticket;
    }

    public CliScheduleResult createSchedule(String name, String command, String triggerSpec) {
        CliScheduleResult nativeResult = nativeScheduler.createJob(name, command, triggerSpec);
        if (nativeResult.success()) {
            auditStore.logSchedule("create-native", nativeResult);
            return nativeResult;
        }
        CliScheduleResult fallback = fallbackScheduler.createJob(name, command, triggerSpec);
        auditStore.logSchedule("create-fallback", fallback);
        return fallback;
    }

    public List<CliScheduleJob> listSchedules() {
        List<CliScheduleJob> internal = fallbackScheduler.listJobs();
        List<CliScheduleJob> nativeJobs = nativeScheduler.listJobs();
        if (nativeJobs == null || nativeJobs.isEmpty()) {
            return internal;
        }
        java.util.ArrayList<CliScheduleJob> merged = new java.util.ArrayList<>(nativeJobs);
        merged.addAll(internal);
        return merged;
    }

    public CliScheduleResult pauseSchedule(String id, String backend) {
        CliScheduleResult result = isNativeBackend(backend)
                ? nativeScheduler.pauseJob(id)
                : fallbackScheduler.pauseJob(id);
        auditStore.logSchedule("pause", result);
        return result;
    }

    public CliScheduleResult deleteSchedule(String id, String backend) {
        CliScheduleResult result = isNativeBackend(backend)
                ? nativeScheduler.deleteJob(id)
                : fallbackScheduler.deleteJob(id);
        auditStore.logSchedule("delete", result);
        return result;
    }

    public CliScheduleResult runNow(String id, String backend) {
        CliScheduleResult result = isNativeBackend(backend)
                ? nativeScheduler.runNow(id)
                : fallbackScheduler.runNow(id);
        auditStore.logSchedule("run-now", result);
        return result;
    }

    public void shutdown() {
        fallbackScheduler.shutdown();
    }

    private boolean isNativeBackend(String backend) {
        if (backend == null || backend.isBlank()) {
            return false;
        }
        return backend.toLowerCase().startsWith("native-");
    }

    private boolean shouldAutoRetry(CliCommandRequest request, CliCommandResult result) {
        if (request == null || result == null) {
            return false;
        }
        if (request.dryRun() || !request.userConfirmed()) {
            return false;
        }
        if (result.success() || !result.executed()) {
            return false;
        }
        if (result.riskLevel() != CommandRiskLevel.LOW) {
            return false;
        }
        String text = ((result.message() == null ? "" : result.message()) + " "
                + (result.stderr() == null ? "" : result.stderr())).toLowerCase();
        return text.contains("not found")
                || text.contains("not recognized")
                || text.contains("no such file")
                || text.contains("cannot find")
                || text.contains("超时")
                || text.contains("timeout")
                || text.contains("directory")
                || text.contains("路径");
    }

    private CliCommandResult withRecoveryHint(CliCommandResult result) {
        if (result == null || result.success()) {
            return result;
        }
        String hint = buildRecoveryHint(result);
        String msg = result.message();
        if (hint.isBlank()) {
            return result;
        }
        String mergedMessage = (msg == null || msg.isBlank()) ? hint : (msg + "；建议：" + hint);
        return new CliCommandResult(
                result.success(),
                result.executed(),
                result.riskLevel(),
                result.exitCode(),
                result.osType(),
                result.command(),
                result.stdout(),
                result.stderr(),
                mergedMessage
        );
    }

    private String buildRecoveryHint(CliCommandResult result) {
        String text = ((result.message() == null ? "" : result.message()) + " "
                + (result.stderr() == null ? "" : result.stderr())).toLowerCase();
        if (text.contains("not found") || text.contains("not recognized") || text.contains("command")) {
            return "请检查命令拼写或先安装缺失工具后重试";
        }
        if (text.contains("permission") || text.contains("denied") || text.contains("权限")) {
            return "请切换到有权限目录或使用具备权限的账号执行";
        }
        if (text.contains("no such file") || text.contains("cannot find") || text.contains("路径")) {
            return "请先确认文件/目录存在，必要时先创建目录";
        }
        if (text.contains("timeout") || text.contains("超时")) {
            return "请增大超时时间，或拆分命令后重试";
        }
        return "";
    }

    private CliCommandResult mergeRetryResult(CliCommandResult first, CliCommandResult retry) {
        if (retry == null) {
            return first;
        }
        if (retry.success()) {
            String msg = "首次执行失败，自动重试后成功";
            String std = appendWithSeparator(first.stderr(), retry.stderr());
            String out = appendWithSeparator(first.stdout(), retry.stdout());
            return new CliCommandResult(
                    true,
                    true,
                    retry.riskLevel(),
                    retry.exitCode(),
                    retry.osType(),
                    retry.command(),
                    out,
                    std,
                    msg
            );
        }
        String mergedErr = appendWithSeparator(first.stderr(), retry.stderr());
        String mergedOut = appendWithSeparator(first.stdout(), retry.stdout());
        return new CliCommandResult(
                false,
                true,
                retry.riskLevel(),
                retry.exitCode(),
                retry.osType(),
                retry.command(),
                mergedOut,
                mergedErr,
                "执行失败（已自动重试 1 次）"
        );
    }

    private String appendWithSeparator(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        if (left.isBlank()) {
            return right;
        }
        if (right.isBlank()) {
            return left;
        }
        return left + "\n---- retry ----\n" + right;
    }

    private boolean isTicketValid(CliCommandRequest request) {
        if (request == null || request.dryRun() || !request.userConfirmed()) {
            return false;
        }
        String ticket = request.confirmTicket();
        if (ticket == null || ticket.isBlank()) {
            return false;
        }
        ConfirmTicket saved = confirmTickets.get(ticket);
        if (saved == null || saved.expiresAtMs() < System.currentTimeMillis()) {
            confirmTickets.remove(ticket);
            return false;
        }
        String reqCmd = request.command() == null ? "" : request.command().trim();
        String reqCwd = safe(request.workingDirectory());
        boolean match = saved.command().equals(reqCmd) && saved.workingDirectory().equals(reqCwd);
        if (match) {
            confirmTickets.remove(ticket); // one-time ticket
        }
        return match;
    }

    private void cleanupExpiredTickets() {
        long now = System.currentTimeMillis();
        confirmTickets.entrySet().removeIf(e -> e.getValue() == null || e.getValue().expiresAtMs() < now);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record ConfirmTicket(String command, String workingDirectory, long expiresAtMs) {
    }
}
