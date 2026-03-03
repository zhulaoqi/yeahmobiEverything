package com.yeahmobi.everything.opsos.notify;

/**
 * Per-channel send result.
 */
public record NotifyResult(
        NotifyChannelType channel,
        boolean success,
        String message
) {
}
