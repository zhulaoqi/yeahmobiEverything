package com.yeahmobi.everything.machineops;

/**
 * Generic schedule operation result.
 */
public record CliScheduleResult(
        boolean success,
        String backend,
        String message,
        CliScheduleJob job
) {
}
