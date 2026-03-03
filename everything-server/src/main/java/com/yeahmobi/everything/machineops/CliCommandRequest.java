package com.yeahmobi.everything.machineops;

/**
 * Request payload for one CLI execution.
 */
public record CliCommandRequest(
        String command,
        String workingDirectory,
        int timeoutSeconds,
        boolean dryRun,
        boolean userConfirmed,
        String confirmTicket
) {
}
