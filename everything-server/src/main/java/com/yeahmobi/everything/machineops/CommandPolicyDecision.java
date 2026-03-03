package com.yeahmobi.everything.machineops;

/**
 * Decision returned by command risk policy engine.
 */
public record CommandPolicyDecision(
        boolean allowed,
        CommandRiskLevel riskLevel,
        boolean requiresConfirm,
        String reason
) {
}
