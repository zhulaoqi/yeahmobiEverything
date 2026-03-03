package com.yeahmobi.everything.skill;

/**
 * Result for installing a skill ZIP package.
 */
public record SkillZipInstallResult(
        boolean success,
        boolean idempotent,
        String skillName,
        String skillVersion,
        String artifactSha256,
        String message
) {
    public static SkillZipInstallResult success(String skillName, String skillVersion, String hash, String message) {
        return new SkillZipInstallResult(true, false, skillName, skillVersion, hash, message);
    }

    public static SkillZipInstallResult idempotent(String skillName, String skillVersion, String hash, String message) {
        return new SkillZipInstallResult(true, true, skillName, skillVersion, hash, message);
    }

    public static SkillZipInstallResult fail(String skillName, String skillVersion, String hash, String message) {
        return new SkillZipInstallResult(false, false, skillName, skillVersion, hash, message);
    }
}

