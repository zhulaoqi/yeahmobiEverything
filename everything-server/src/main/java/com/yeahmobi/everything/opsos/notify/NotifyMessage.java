package com.yeahmobi.everything.opsos.notify;

/**
 * Canonical notification payload for all channels.
 */
public record NotifyMessage(
        String topic,
        String title,
        String summary,
        String detailText,
        String detailHtml
) {
}
