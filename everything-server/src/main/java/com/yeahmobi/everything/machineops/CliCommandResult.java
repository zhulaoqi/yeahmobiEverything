package com.yeahmobi.everything.machineops;

/**
 * Result payload for command execution.
 */
public record CliCommandResult(
        boolean success,
        boolean executed,
        CommandRiskLevel riskLevel,
        int exitCode,
        String osType,
        String command,
        String stdout,
        String stderr,
        String message
) {
}
