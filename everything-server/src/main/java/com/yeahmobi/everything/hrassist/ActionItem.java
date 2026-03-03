package com.yeahmobi.everything.hrassist;

/**
 * Action item generated from an HR case.
 */
public record ActionItem(
        String id,
        String caseId,
        String actionType,
        String title,
        String dueAt,
        String priority,
        HrActionStatus status,
        String sourceEvidence,
        String createdAt,
        String updatedAt
) {
}

