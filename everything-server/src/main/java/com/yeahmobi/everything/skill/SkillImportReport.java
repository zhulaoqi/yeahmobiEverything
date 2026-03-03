package com.yeahmobi.everything.skill;

/**
 * Detailed import report for skill repository imports.
 */
public record SkillImportReport(
        boolean success,
        String reason,
        int scanned,
        int imported,
        int repaired,
        int skipped,
        int failed,
        int localizationAttempted,
        int localizationSucceeded,
        int localizationFailed
) {
    public int changedCount() {
        return imported + repaired;
    }

    public boolean hasLocalizationFailures() {
        return localizationFailed > 0;
    }
}
