package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.repository.mysql.MySQLDatabaseManager;
import com.yeahmobi.everything.repository.mysql.SkillRepositoryImpl;

import java.util.List;

/**
 * One-shot runner that standardizes legacy skill rows to structured manifest fields.
 */
public class SkillStandardizationRunner {

    public static void main(String[] args) throws Exception {
        Config config = Config.getInstance();
        MySQLDatabaseManager mysql = new MySQLDatabaseManager(config);
        mysql.initialize();

        SkillRepositoryImpl skillRepository = new SkillRepositoryImpl(mysql);
        AnthropicSkillImporter parser = new AnthropicSkillImporter(skillRepository, null);

        int scanned = 0;
        int updated = 0;
        List<SkillAdmin> all = skillRepository.getAllSkills();
        for (SkillAdmin s : all) {
            scanned++;
            String embeddedMarkdown = extractEmbeddedSkillMarkdown(s.promptTemplate());
            if (embeddedMarkdown == null) {
                continue;
            }

            SkillManifest manifest = parser.parseSkillMarkdown(
                    embeddedMarkdown,
                    s.name() != null ? s.name() : "skill",
                    s.category() != null ? s.category() : "skills"
            );

            SkillAdmin normalized = new SkillAdmin(
                    s.id(),
                    manifest.name(),
                    manifest.description(),
                    s.icon(),
                    s.category(),
                    s.enabled(),
                    (s.usageGuide() == null || s.usageGuide().isBlank()) ? manifest.usageGuide() : s.usageGuide(),
                    (s.examples() == null || s.examples().isEmpty()) ? manifest.examples() : s.examples(),
                    s.i18nJson(),
                    s.source(),
                    (s.sourceLang() == null || s.sourceLang().isBlank()) ? manifest.sourceLang() : s.sourceLang(),
                    s.qualityTier(),
                    (s.toolIds() == null || s.toolIds().isEmpty()) ? manifest.toolIds() : s.toolIds(),
                    (s.toolGroups() == null || s.toolGroups().isEmpty()) ? manifest.toolGroups() : s.toolGroups(),
                    (s.contextPolicy() == null || s.contextPolicy().isBlank() || "default".equalsIgnoreCase(s.contextPolicy()))
                            ? manifest.contextPolicy()
                            : s.contextPolicy(),
                    s.type(),
                    s.kind(),
                    manifest.promptTemplate(),
                    s.executionMode(),
                    s.createdAt()
            );

            if (hasDiff(s, normalized)) {
                skillRepository.updateSkill(normalized);
                updated++;
                System.out.println("Standardized skill: " + s.id() + " -> " + normalized.name());
            }
        }

        System.out.println("Skill standardization done. scanned=" + scanned + ", updated=" + updated);
    }

    private static String extractEmbeddedSkillMarkdown(String promptTemplate) {
        if (promptTemplate == null || promptTemplate.isBlank()) {
            return null;
        }
        int start = promptTemplate.indexOf("---");
        if (start < 0) {
            return null;
        }
        int end = promptTemplate.lastIndexOf("用户输入:");
        if (end <= start) {
            end = promptTemplate.length();
        }
        String markdown = promptTemplate.substring(start, end).trim();
        return markdown.startsWith("---") ? markdown : null;
    }

    private static boolean hasDiff(SkillAdmin a, SkillAdmin b) {
        return !safeEquals(a.name(), b.name())
                || !safeEquals(a.description(), b.description())
                || !safeEquals(a.usageGuide(), b.usageGuide())
                || !safeEquals(a.promptTemplate(), b.promptTemplate())
                || !safeEquals(a.sourceLang(), b.sourceLang())
                || !safeEquals(a.contextPolicy(), b.contextPolicy())
                || !safeEquals(a.toolIds(), b.toolIds())
                || !safeEquals(a.toolGroups(), b.toolGroups())
                || !safeEquals(a.examples(), b.examples());
    }

    private static boolean safeEquals(Object a, Object b) {
        return java.util.Objects.equals(a, b);
    }
}

