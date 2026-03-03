package com.yeahmobi.everything.machineops;

import java.util.List;
import java.util.Locale;

/**
 * Risk policy for local command execution.
 */
public class CommandPolicyEngine {

    private static final List<String> HARD_BLOCK_KEYWORDS = List.of(
            "rm -rf /",
            "shutdown -h",
            "reboot",
            "mkfs",
            "dd if=",
            "diskpart",
            "format c:",
            "net user",
            "passwd root",
            "reg delete hklm",
            "curl http://169.254.169.254",
            "wget http://169.254.169.254"
    );

    private static final List<String> MEDIUM_RISK_KEYWORDS = List.of(
            "rm -rf",
            "kill -9",
            "taskkill /f",
            "chmod 777",
            "chown -r",
            "crontab -r",
            "schtasks /delete",
            "launchctl remove"
    );

    public CommandPolicyDecision evaluate(String command, String workingDirectory) {
        String cmd = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (cmd.isBlank()) {
            return new CommandPolicyDecision(false, CommandRiskLevel.HIGH, false, "命令为空");
        }
        if (cmd.contains("sudo ") || cmd.startsWith("sudo")) {
            return new CommandPolicyDecision(false, CommandRiskLevel.HIGH, false, "默认禁用 sudo 提权执行");
        }
        for (String blocked : HARD_BLOCK_KEYWORDS) {
            if (cmd.contains(blocked)) {
                return new CommandPolicyDecision(false, CommandRiskLevel.HIGH, false, "命令命中高危黑名单: " + blocked);
            }
        }
        for (String medium : MEDIUM_RISK_KEYWORDS) {
            if (cmd.contains(medium)) {
                return new CommandPolicyDecision(true, CommandRiskLevel.MEDIUM, true, "命令存在中风险操作，需用户确认");
            }
        }
        if (workingDirectory != null && workingDirectory.contains("..")) {
            return new CommandPolicyDecision(true, CommandRiskLevel.MEDIUM, true, "工作目录包含上跳路径，需确认");
        }
        return new CommandPolicyDecision(true, CommandRiskLevel.LOW, false, "低风险命令");
    }
}
