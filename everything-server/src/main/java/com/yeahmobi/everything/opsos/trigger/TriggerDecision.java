package com.yeahmobi.everything.opsos.trigger;

/**
 * Decision for whether trigger should fire and its idempotency key.
 */
public record TriggerDecision(
        boolean fire,
        String idempotencyKey,
        String reason
) {
}
