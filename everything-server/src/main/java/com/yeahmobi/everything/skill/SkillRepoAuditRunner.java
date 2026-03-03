package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.repository.mysql.MySQLDatabaseManager;
import com.yeahmobi.everything.repository.mysql.SkillRepositoryImpl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Audit skills in a local anthropics/skills clone and optionally import missing ones.
 *
 * Usage:
 *   java ... SkillRepoAuditRunner /path/to/skills/root [--import]
 */
public class SkillRepoAuditRunner {

    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            System.out.println("Usage: SkillRepoAuditRunner <repoPath> [--import]");
            return;
        }
        String repoPath = args[0];
        boolean doImport = args.length > 1 && "--import".equalsIgnoreCase(args[1]);

        Config config = Config.getInstance();
        MySQLDatabaseManager mysql = new MySQLDatabaseManager(config);
        mysql.initialize();
        SkillRepositoryImpl skillRepository = new SkillRepositoryImpl(mysql);
        AnthropicSkillImporter importer = new AnthropicSkillImporter(skillRepository, null);

        Path root = Path.of(repoPath);
        if (!Files.exists(root)) {
            System.out.println("Path not found: " + repoPath);
            return;
        }

        List<String> missing = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> skillFiles = stream
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .toList();
            for (Path file : skillFiles) {
                scanned++;
                String relPath = root.relativize(file.getParent()).toString().replace("\\", "/");
                String sourceId = "anthropic/" + relPath;
                String id = UUID.nameUUIDFromBytes(sourceId.getBytes(StandardCharsets.UTF_8)).toString();
                var existing = skillRepository.getSkill(id);
                if (existing.isEmpty()) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    String defaultName = file.getParent().getFileName().toString();
                    String defaultCategory = relPath.contains("/") ? relPath.split("/")[0] : "skills";
                    SkillManifest manifest = importer.parseSkillMarkdown(content, defaultName, defaultCategory);
                    missing.add(relPath + " -> " + manifest.name());
                } else {
                    var hit = existing.get();
                    matched.add(relPath + " -> id=" + id
                            + ", db_name=" + hit.name()
                            + ", source=" + hit.source()
                            + ", category=" + hit.category());
                }
            }
        }

        System.out.println("Audit scanned SKILL.md files: " + scanned);
        System.out.println("Matched skills in DB: " + matched.size());
        for (String m : matched) {
            System.out.println("  MATCHED: " + m);
        }
        System.out.println("Missing skills in DB: " + missing.size());
        for (String m : missing) {
            System.out.println("  MISSING: " + m);
        }

        if (doImport) {
            int changed = importer.importFromPath(repoPath);
            System.out.println("Import changed count: " + changed);
        }
    }
}

