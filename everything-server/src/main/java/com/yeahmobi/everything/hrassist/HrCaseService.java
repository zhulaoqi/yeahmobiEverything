package com.yeahmobi.everything.hrassist;

import java.util.List;

/**
 * Service for HR execution case lifecycle.
 */
public interface HrCaseService {

    CandidateCase createCase(String candidateName,
                             String position,
                             HrCaseStage stage,
                             String owner,
                             HrRiskLevel riskLevel,
                             String nextAction,
                             String dueAt);

    CandidateCase updateCase(String caseId,
                             HrCaseStage stage,
                             HrRiskLevel riskLevel,
                             String nextAction,
                             String dueAt,
                             HrCaseStatus status);

    ActionItem addAction(String caseId,
                         String actionType,
                         String title,
                         String dueAt,
                         String priority,
                         String sourceEvidence);

    ActionItem updateActionStatus(String actionId, HrActionStatus status);

    EvidenceRef addEvidence(String caseId, String sourceType, String sourcePathOrUrl, String snippet, double confidence);

    ReminderEvent addReminder(String actionId, String remindAt, String channel);

    List<CandidateCase> listCases(HrCaseStatus statusFilter);

    HrCaseSnapshot getSnapshot(String caseId);
}

