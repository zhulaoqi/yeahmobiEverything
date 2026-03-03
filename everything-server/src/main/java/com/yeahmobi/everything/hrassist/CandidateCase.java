package com.yeahmobi.everything.hrassist;

/**
 * Core HR execution case.
 */
public record CandidateCase(
        String caseId,
        String candidateName,
        String position,
        HrCaseStage stage,
        String owner,
        HrRiskLevel riskLevel,
        String nextAction,
        String dueAt,
        HrCaseStatus status,
        String createdAt,
        String updatedAt
) {
}

