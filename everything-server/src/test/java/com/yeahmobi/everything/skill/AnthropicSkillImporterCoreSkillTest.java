package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnthropicSkillImporterCoreSkillTest {

    @TempDir
    Path tempDir;

    @Test
    void applyCoreSkillProfileEnhancesVercelDeploySkill() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        CacheService cacheService = mock(CacheService.class);
        AnthropicSkillImporter importer = new AnthropicSkillImporter(skillRepository, cacheService);

        SkillManifest manifest = new SkillManifest(
                "vercel-deploy",
                "Deploy apps to Vercel",
                "skills",
                "en",
                SkillExecutionMode.SINGLE,
                List.of(),
                "usage",
                "prompt",
                List.of("llm"),
                List.of("llm-only"),
                "standard"
        );

        SkillManifest profiled = importer.applyCoreSkillProfile(
                manifest,
                "skills/claude.ai/vercel-deploy-claimable"
        );

        assertEquals(SkillExecutionMode.MULTI, profiled.executionMode());
        assertEquals("advanced", profiled.contextPolicy());
        assertTrue(profiled.toolIds().contains("mcp-bridge"));
        assertTrue(profiled.toolGroups().contains("external-capability"));
        assertTrue(profiled.promptTemplate().contains("deployment_url"));
    }

    @Test
    void importRepairsExistingDeploySkillWithCoreProfile() throws Exception {
        SkillRepository skillRepository = mock(SkillRepository.class);
        AnthropicSkillImporter importer = new AnthropicSkillImporter(skillRepository, null);

        Path skillDir = tempDir.resolve("skills").resolve("claude.ai").resolve("vercel-deploy-claimable");
        Files.createDirectories(skillDir);
        Path skillFile = skillDir.resolve("SKILL.md");
        String content = """
---
name: vercel-deploy
description: Deploy apps to Vercel
category: skills
---
Use this skill to deploy.
""";
        Files.writeString(skillFile, content, StandardCharsets.UTF_8);

        String relPath = "skills/claude.ai/vercel-deploy-claimable";
        String sourceId = "anthropic/" + relPath;
        String id = UUID.nameUUIDFromBytes(sourceId.getBytes(StandardCharsets.UTF_8)).toString();

        SkillAdmin existing = new SkillAdmin(
                id,
                "vercel-deploy",
                "Deploy apps to Vercel",
                "default.png",
                "skills",
                true,
                "Use this skill to deploy.",
                List.of(),
                null,
                "external",
                "en",
                "basic",
                List.of(),
                List.of("llm-only"),
                "standard",
                SkillType.GENERAL,
                SkillKind.PROMPT_ONLY,
                "prompt",
                SkillExecutionMode.SINGLE,
                System.currentTimeMillis()
        );

        when(skillRepository.getSkill(id)).thenReturn(Optional.of(existing));

        SkillImportReport report = importer.importFromPathDetailed(tempDir.toString());

        assertTrue(report.success());
        assertEquals(1, report.repaired());
        verify(skillRepository, never()).saveSkill(any());
        verify(skillRepository).updateSkill(argThat(updated ->
                updated.toolIds().contains("mcp-bridge")
                        && updated.toolGroups().contains("external-capability")
                        && "advanced".equals(updated.contextPolicy())
                        && SkillExecutionMode.MULTI.equals(updated.executionMode())
        ));
    }

    @Test
    void applyCoreSkillProfileEnhancesDocxSkill() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        AnthropicSkillImporter importer = new AnthropicSkillImporter(skillRepository, null);

        SkillManifest manifest = new SkillManifest(
                "docx",
                "Generate Word documents",
                "skills",
                "en",
                SkillExecutionMode.MULTI,
                List.of(),
                "usage",
                "prompt",
                List.of(),
                List.of("llm-only"),
                "advanced"
        );

        SkillManifest profiled = importer.applyCoreSkillProfile(manifest, "skills/docx");
        assertEquals(SkillExecutionMode.SINGLE, profiled.executionMode());
        assertEquals("standard", profiled.contextPolicy());
        assertTrue(profiled.toolIds().contains("docx-generator"));
        assertTrue(profiled.toolGroups().contains("document"));
        assertTrue(profiled.promptTemplate().contains("文档文件路径"));
    }

    @Test
    void importRepairsExistingDocxSkillWithCoreProfile() throws Exception {
        SkillRepository skillRepository = mock(SkillRepository.class);
        AnthropicSkillImporter importer = new AnthropicSkillImporter(skillRepository, null);

        Path skillDir = tempDir.resolve("skills").resolve("docx");
        Files.createDirectories(skillDir);
        Path skillFile = skillDir.resolve("SKILL.md");
        String content = """
---
name: docx
description: Generate Word documents
category: skills
---
Generate reports.
""";
        Files.writeString(skillFile, content, StandardCharsets.UTF_8);

        String relPath = "skills/docx";
        String sourceId = "anthropic/" + relPath;
        String id = UUID.nameUUIDFromBytes(sourceId.getBytes(StandardCharsets.UTF_8)).toString();

        SkillAdmin existing = new SkillAdmin(
                id,
                "docx",
                "Generate Word documents",
                "default.png",
                "skills",
                true,
                "Generate reports.",
                List.of(),
                null,
                "external",
                "en",
                "basic",
                List.of(),
                List.of(),
                "default",
                SkillType.GENERAL,
                SkillKind.PROMPT_ONLY,
                "prompt",
                SkillExecutionMode.MULTI,
                System.currentTimeMillis()
        );

        when(skillRepository.getSkill(id)).thenReturn(Optional.of(existing));

        SkillImportReport report = importer.importFromPathDetailed(tempDir.toString());

        assertTrue(report.success());
        assertEquals(1, report.repaired());
        verify(skillRepository).updateSkill(argThat(updated ->
                updated.toolIds().contains("docx-generator")
                        && updated.toolGroups().contains("document")
                        && "standard".equals(updated.contextPolicy())
                        && SkillExecutionMode.SINGLE.equals(updated.executionMode())
        ));
    }
}
