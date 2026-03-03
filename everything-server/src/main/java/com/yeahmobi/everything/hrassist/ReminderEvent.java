package com.yeahmobi.everything.hrassist;

/**
 * Reminder event bound to an action item.
 */
public record ReminderEvent(
        String id,
        String actionId,
        String remindAt,
        String channel,
        String status,
        String createdAt
) {
}

