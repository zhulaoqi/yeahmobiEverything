package com.yeahmobi.everything.machineops;

/**
 * Cross-platform command execution adapter.
 */
public interface OsCommandAdapter {

    CliCommandResult execute(CliCommandRequest request, CommandPolicyDecision policyDecision);
}
