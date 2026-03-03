package com.yeahmobi.everything.opsos.notify;

import java.util.List;

/**
 * Multi-channel dispatch summary.
 */
public record NotifyDispatchResult(
        boolean success,
        List<NotifyResult> channelResults
) {
}
