package com.yeahmobi.everything.machineops;

/**
 * Scheduled CLI job model.
 */
public record CliScheduleJob(
        String id,
        String name,
        String command,
        String triggerSpec,
        boolean enabled,
        String backend,
        String nextRunAt,
        String lastRunAt,
        String lastStatus
) {
}
