package com.yeahmobi.everything.hrassist;

import java.util.List;

/**
 * Read model for one HR case and its linked objects.
 */
public record HrCaseSnapshot(
        CandidateCase candidateCase,
        List<ActionItem> actions,
        List<EvidenceRef> evidences,
        List<ReminderEvent> reminders
) {
}

