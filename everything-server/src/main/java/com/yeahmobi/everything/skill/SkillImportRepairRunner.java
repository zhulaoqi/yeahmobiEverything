package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.repository.mysql.MySQLDatabaseManager;
import com.yeahmobi.everything.repository.mysql.SkillRepositoryImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * One-shot runner to re-import/repair external skills.
 *
 * <p>Usage:</p>
 * <pre>
 *   java ... com.yeahmobi.everything.skill.SkillImportRepairRunner [optionalRepoPath]
 * </pre>
 */
public class SkillImportRepairRunner {

    private static final Path CURSOR_SKILLS_DIR =
            Path.of(System.getProperty("user.home"), ".cursor", "skills-cursor");
    private static final Path CURSOR_SKILLS_ALT_DIR =
            Path.of(System.getProperty("user.home"), ".cursor", "skills");
    private static final Path ANTHROPIC_SKILLS_DIR =
            Path.of(System.getProperty("user.home"), "anthropic", "skills");

    public static void main(String[] args) throws Exception {
        Config config = Config.getInstance();
        MySQLDatabaseManager mysql = new MySQLDatabaseManager(config);
        mysql.initialize();

        SkillRepositoryImpl skillRepository = new SkillRepositoryImpl(mysql);
        AnthropicSkillImporter importer = new AnthropicSkillImporter(skillRepository, null);

        System.out.println("=== Skill Repair Runner ===");
        printSummary("Before", skillRepository.getAllSkills());

        List<String> candidatePaths = collectCandidatePaths(config, args);
        int totalChanged = 0;
        for (String p : candidatePaths) {
            if (p == null || p.isBlank()) {
                continue;
            }
            Path path = Path.of(p);
            if (!Files.exists(path)) {
                continue;
            }
            System.out.println("Re-import path: " + path);
            int changed = importer.importFromPath(path.toString());
            System.out.println("Changed by this path: " + changed);
            totalChanged += changed;
        }

        printSummary("After", skillRepository.getAllSkills());
        System.out.println("Total changed: " + totalChanged);
    }

    private static List<String> collectCandidatePaths(Config config, String[] args) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (args != null) {
            for (String arg : args) {
                if (arg != null && !arg.isBlank()) {
                    paths.add(arg.trim());
                }
            }
        }
        String configured = config.getAnthropicSkillPath();
        if (configured != null && !configured.isBlank()) {
            paths.add(configured.trim());
        }
        paths.add(CURSOR_SKILLS_DIR.toString());
        paths.add(CURSOR_SKILLS_ALT_DIR.toString());
        paths.add(ANTHROPIC_SKILLS_DIR.toString());
        return new ArrayList<>(paths);
    }

    private static void printSummary(String title, List<SkillAdmin> all) {
        int total = all.size();
        int external = 0;
        int missingUsage = 0;
        int missingPrompt = 0;
        int missingContext = 0;
        int missingToolMeta = 0;

        for (SkillAdmin s : all) {
            boolean isExternal = "external".equalsIgnoreCase(s.source());
            if (!isExternal) {
                continue;
            }
            external++;
            if (s.usageGuide() == null || s.usageGuide().isBlank()) {
                missingUsage++;
            }
            if (s.promptTemplate() == null || s.promptTemplate().isBlank()) {
                missingPrompt++;
            }
            if (s.contextPolicy() == null || s.contextPolicy().isBlank()) {
                missingContext++;
            }
            if ((s.toolIds() == null || s.toolIds().isEmpty())
                    && (s.toolGroups() == null || s.toolGroups().isEmpty())) {
                missingToolMeta++;
            }
        }

        System.out.println("--- " + title + " ---");
        System.out.println("total skills: " + total);
        System.out.println("external skills: " + external);
        System.out.println("external missing usage_guide: " + missingUsage);
        System.out.println("external missing prompt_template: " + missingPrompt);
        System.out.println("external missing context_policy: " + missingContext);
        System.out.println("external missing tool metadata: " + missingToolMeta);
    }
}

