package com.yeahmobi.everything.opsos.trigger;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Trigger policy engine with idempotency key generation.
 */
public class TriggerEngine {

    public TriggerDecision timeBeforeDue(String todoId,
                                         LocalDateTime dueAt,
                                         int leadMinutes,
                                         LocalDateTime now) {
        if (todoId == null || todoId.isBlank() || dueAt == null || now == null) {
            return new TriggerDecision(false, "", "invalid-args");
        }
        LocalDateTime remindAt = dueAt.minusMinutes(Math.max(1, leadMinutes));
        String key = "todo|" + todoId.trim() + "|due|" + dueAt + "|lead|" + Math.max(1, leadMinutes);
        if (remindAt.isAfter(now)) {
            return new TriggerDecision(false, key, "not-due");
        }
        return new TriggerDecision(true, key, "due");
    }

    public TriggerDecision statusChanged(String todoId, String oldStatus, String newStatus) {
        String oldVal = oldStatus == null ? "" : oldStatus.trim().toLowerCase();
        String newVal = newStatus == null ? "" : newStatus.trim().toLowerCase();
        String key = "todo|" + (todoId == null ? "" : todoId.trim()) + "|status|" + oldVal + "->" + newVal;
        if (todoId == null || todoId.isBlank() || oldVal.equals(newVal) || newVal.isBlank()) {
            return new TriggerDecision(false, key, "unchanged");
        }
        return new TriggerDecision(true, key, "status-changed");
    }

    public TriggerDecision manualTest(String todoId, long timestampMs) {
        String key = "todo|" + (todoId == null ? "" : todoId.trim()) + "|manual|" + timestampMs;
        if (todoId == null || todoId.isBlank()) {
            return new TriggerDecision(false, key, "invalid-args");
        }
        return new TriggerDecision(true, key, "manual-test");
    }

    public TriggerDecision dailyBrief(LocalDate date, String channelName) {
        if (date == null) {
            return new TriggerDecision(false, "", "invalid-date");
        }
        String channel = channelName == null || channelName.isBlank() ? "unknown" : channelName.trim().toLowerCase();
        String key = "daily-brief|" + date + "|" + channel;
        return new TriggerDecision(true, key, "daily-brief");
    }
}
