package com.yeahmobi.everything.machineops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Default OS adapter for executing shell commands.
 */
public class DefaultOsCommandAdapter implements OsCommandAdapter {

    private static final Logger log = LoggerFactory.getLogger(DefaultOsCommandAdapter.class);

    @Override
    public CliCommandResult execute(CliCommandRequest request, CommandPolicyDecision policyDecision) {
        String command = request == null ? "" : request.command();
        String cwd = request == null ? null : request.workingDirectory();
        int timeoutSec = request == null ? 30 : Math.max(1, Math.min(300, request.timeoutSeconds()));
        String osType = detectOs();
        if (command == null || command.isBlank()) {
            return new CliCommandResult(false, false,
                    policyDecision != null ? policyDecision.riskLevel() : CommandRiskLevel.HIGH,
                    -1, osType, command, "", "", "命令为空");
        }
        if (request != null && request.dryRun()) {
            return new CliCommandResult(true, false,
                    policyDecision != null ? policyDecision.riskLevel() : CommandRiskLevel.LOW,
                    0, osType, command, "", "", "dry-run: 仅预览命令，不执行");
        }
        if (policyDecision != null && policyDecision.requiresConfirm() && (request == null || !request.userConfirmed())) {
            return new CliCommandResult(false, false, policyDecision.riskLevel(),
                    -1, osType, command, "", "", "命令需要用户确认后执行");
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(buildShellCommand(command, osType));
            if (cwd != null && !cwd.isBlank()) {
                pb.directory(new File(cwd));
            }
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CliCommandResult(false, true,
                        policyDecision != null ? policyDecision.riskLevel() : CommandRiskLevel.MEDIUM,
                        -1, osType, command, "", "", "命令执行超时(" + timeoutSec + "s)");
            }
            String stdout = truncate(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8), 6000);
            String stderr = truncate(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8), 4000);
            int exitCode = process.exitValue();
            return new CliCommandResult(
                    exitCode == 0,
                    true,
                    policyDecision != null ? policyDecision.riskLevel() : CommandRiskLevel.LOW,
                    exitCode,
                    osType,
                    command,
                    stdout,
                    stderr,
                    exitCode == 0 ? "执行成功" : "执行失败"
            );
        } catch (Exception e) {
            log.warn("Command execution failed: {}", e.getMessage());
            return new CliCommandResult(false, true,
                    policyDecision != null ? policyDecision.riskLevel() : CommandRiskLevel.MEDIUM,
                    -1, osType, command, "", e.getMessage(), "命令执行异常");
        }
    }

    private List<String> buildShellCommand(String command, String osType) {
        List<String> cmd = new ArrayList<>();
        if ("windows".equals(osType)) {
            cmd.add("powershell");
            cmd.add("-NoProfile");
            cmd.add("-Command");
            cmd.add(command);
            return cmd;
        }
        if ("macos".equals(osType)) {
            cmd.add("/bin/zsh");
            cmd.add("-lc");
            cmd.add(command);
            return cmd;
        }
        cmd.add("/bin/bash");
        cmd.add("-lc");
        cmd.add(command);
        return cmd;
    }

    static String detectOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac")) {
            return "macos";
        }
        if (os.contains("nux") || os.contains("nix")) {
            return "linux";
        }
        return "unknown";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "\n...[输出已截断]";
    }
}
