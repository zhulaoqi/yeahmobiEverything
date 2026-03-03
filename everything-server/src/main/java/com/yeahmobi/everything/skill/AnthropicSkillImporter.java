package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.SkillRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.Objects;

/**
 * Imports Anthropic skills from a local clone of https://github.com/anthropics/skills.
 */
public class AnthropicSkillImporter {

    private final SkillRepository skillRepository;
    private final CacheService cacheService;

    public AnthropicSkillImporter(SkillRepository skillRepository, CacheService cacheService) {
        this.skillRepository = skillRepository;
        this.cacheService = cacheService;
    }

    public int importFromPath(String repoPath) {
        return importFromPathDetailed(repoPath).changedCount();
    }

    public SkillImportReport importFromPathDetailed(String repoPath) {
        if (repoPath == null || repoPath.isBlank()) {
            return new SkillImportReport(false, "导入路径为空", 0, 0, 0, 0, 0, 0, 0, 0);
        }
        Path root = Paths.get(repoPath);
        if (!Files.exists(root)) {
            return new SkillImportReport(false, "仓库路径不存在: " + repoPath, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        if (!Files.isDirectory(root)) {
            return new SkillImportReport(false, "导入路径不是目录: " + repoPath, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        SkillLocalizationService localizer = buildLocalizer();
        int imported = 0;
        int repaired = 0;
        int skipped = 0;
        int failed = 0;
        int localizationAttempted = 0;
        int localizationSucceeded = 0;
        int localizationFailed = 0;
        List<Path> skillFiles;

        try (Stream<Path> paths = Files.walk(root)) {
            skillFiles = paths
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .toList();
        } catch (IOException e) {
            return new SkillImportReport(false, "扫描仓库失败: " + e.getMessage(), 0, 0, 0, 0, 0, 0, 0, 0);
        }

        int scanned = skillFiles.size();
        if (scanned == 0) {
            return new SkillImportReport(
                    true,
                    "未发现可导入的 SKILL.md 文件，请检查仓库结构是否为 skills/*/SKILL.md",
                    0, 0, 0, 0, 0, 0, 0, 0
            );
        }

        for (Path file : skillFiles) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String relPath = root.relativize(file.getParent()).toString().replace("\\", "/");
                String sourceId = "anthropic/" + relPath;
                String defaultName = file.getParent().getFileName().toString();
                String defaultCategory = relPath.contains("/") ? relPath.split("/")[0] : "Anthropic";
                SkillManifest manifest = applyCoreSkillProfile(
                        parseManifest(content, defaultName, defaultCategory),
                        relPath
                );

                String name = manifest.name();
                String description = manifest.description();
                String category = manifest.category();
                String promptTemplate = manifest.promptTemplate();
                String id = UUID.nameUUIDFromBytes(sourceId.getBytes(StandardCharsets.UTF_8)).toString();

                var existingOpt = skillRepository.getSkill(id);
                if (existingOpt.isPresent()) {
                    SkillAdmin repairedSkill = mergeForRepair(existingOpt.get(), manifest);
                    if (hasMeaningfulDiff(existingOpt.get(), repairedSkill)) {
                        skillRepository.updateSkill(repairedSkill);
                        repaired++;
                    } else {
                        skipped++;
                    }
                    continue;
                }

                SkillAdmin skill = new SkillAdmin(
                        id,
                        name,
                        description,
                        "default.png",
                        category,
                        true,
                        manifest.usageGuide(),
                        manifest.examples(),
                        null,           // i18nJson
                        "external",     // source
                        manifest.sourceLang(),
                        "basic",        // qualityTier
                        manifest.toolIds(),
                        manifest.toolGroups(),
                        manifest.contextPolicy(),
                        SkillType.GENERAL,
                        SkillKind.PROMPT_ONLY,
                        promptTemplate,
                        manifest.executionMode(),
                        System.currentTimeMillis()
                );
                skillRepository.saveSkill(skill);

                // Best-effort localization after import (non-blocking on failures)
                if (localizer != null) {
                    localizationAttempted++;
                    var payloadOpt = localizer.localizeToZhCn(skill);
                    if (payloadOpt.isPresent()) {
                        var payload = payloadOpt.get();
                        String localizedDescription = payload.localizedOneLine();
                        String localizedUsageGuide = payload.localizedUsageGuide();
                        SkillAdmin updated = new SkillAdmin(
                                skill.id(),
                                skill.name(),
                                (localizedDescription != null && !localizedDescription.isBlank())
                                        ? localizedDescription
                                        : skill.description(),
                                skill.icon(),
                                skill.category(),
                                skill.enabled(),
                                (localizedUsageGuide != null && !localizedUsageGuide.isBlank())
                                        ? localizedUsageGuide
                                        : skill.usageGuide(),
                                (payload.exampleInputs() != null && !payload.exampleInputs().isEmpty())
                                        ? payload.exampleInputs()
                                        : skill.examples(),
                                payload.i18nJson(),
                                skill.source(),
                                skill.sourceLang(),
                                skill.qualityTier(),
                                skill.toolIds(),
                                skill.toolGroups(),
                                skill.contextPolicy(),
                                skill.type(),
                                skill.kind(),
                                skill.promptTemplate(),
                                skill.executionMode(),
                                skill.createdAt()
                        );
                        try {
                            skillRepository.updateSkill(updated);
                            localizationSucceeded++;
                        } catch (Exception ignored) {
                            localizationFailed++;
                            // ignore localization persistence failures
                        }
                    } else {
                        localizationFailed++;
                    }
                }
                imported++;
            } catch (Exception ex) {
                failed++;
            }
        }

        if ((imported > 0 || repaired > 0) && cacheService != null) {
            try {
                cacheService.invalidateSkillCache();
            } catch (Exception ignored) {
                // ignore cache failures
            }
        }
        return new SkillImportReport(
                true, "", scanned, imported, repaired, skipped, failed,
                localizationAttempted, localizationSucceeded, localizationFailed
        );
    }

    private SkillAdmin mergeForRepair(SkillAdmin existing, SkillManifest manifest) {
        if (existing == null) {
            return null;
        }
        if (manifest == null) {
            return existing;
        }

        String nextDescription = existing.description();
        if (nextDescription == null || nextDescription.isBlank() || nextDescription.startsWith("Anthropic skill:")) {
            nextDescription = manifest.description();
        }

        String nextUsage = (existing.usageGuide() == null || existing.usageGuide().isBlank())
                ? manifest.usageGuide()
                : existing.usageGuide();
        List<String> nextExamples = (existing.examples() == null || existing.examples().isEmpty())
                ? manifest.examples()
                : existing.examples();
        String nextPrompt = (existing.promptTemplate() == null || existing.promptTemplate().isBlank())
                ? manifest.promptTemplate()
                : existing.promptTemplate();
        String nextSourceLang = (existing.sourceLang() == null || existing.sourceLang().isBlank())
                ? manifest.sourceLang()
                : existing.sourceLang();
        boolean deploymentCore = isDeploymentCoreSkill(manifest);
        boolean docxCore = isDocxCoreSkill(manifest, null);
        List<String> nextToolIds = deploymentCore
                ? mergeDistinct(existing.toolIds(), manifest.toolIds())
                : (docxCore
                    ? mergeDistinct(existing.toolIds(), manifest.toolIds())
                    : ((existing.toolIds() == null || existing.toolIds().isEmpty())
                        ? manifest.toolIds()
                        : existing.toolIds()));
        List<String> nextToolGroups = deploymentCore
                ? mergeDistinct(existing.toolGroups(), manifest.toolGroups())
                : (docxCore
                    ? mergeDistinct(existing.toolGroups(), manifest.toolGroups())
                    : ((existing.toolGroups() == null || existing.toolGroups().isEmpty())
                        ? manifest.toolGroups()
                        : existing.toolGroups()));
        String nextContextPolicy = deploymentCore
                ? "advanced"
                : (docxCore
                    ? "standard"
                    : ((existing.contextPolicy() == null || existing.contextPolicy().isBlank()
                        || "default".equalsIgnoreCase(existing.contextPolicy()))
                        ? manifest.contextPolicy()
                        : existing.contextPolicy()));
        SkillExecutionMode nextExecutionMode = deploymentCore
                ? SkillExecutionMode.MULTI
                : (docxCore
                    ? SkillExecutionMode.SINGLE
                    : (existing.executionMode() != null ? existing.executionMode() : manifest.executionMode()));

        return new SkillAdmin(
                existing.id(),
                existing.name(),
                nextDescription,
                existing.icon(),
                existing.category(),
                existing.enabled(),
                nextUsage,
                nextExamples,
                existing.i18nJson(),
                existing.source(),
                nextSourceLang,
                existing.qualityTier(),
                nextToolIds,
                nextToolGroups,
                nextContextPolicy,
                existing.type(),
                existing.kind(),
                nextPrompt,
                nextExecutionMode,
                existing.createdAt()
        );
    }

    private boolean hasMeaningfulDiff(SkillAdmin a, SkillAdmin b) {
        if (a == null || b == null) {
            return false;
        }
        return !Objects.equals(a.description(), b.description())
                || !Objects.equals(a.usageGuide(), b.usageGuide())
                || !Objects.equals(a.examples(), b.examples())
                || !Objects.equals(a.promptTemplate(), b.promptTemplate())
                || !Objects.equals(a.sourceLang(), b.sourceLang())
                || !Objects.equals(a.toolIds(), b.toolIds())
                || !Objects.equals(a.toolGroups(), b.toolGroups())
                || !Objects.equals(a.contextPolicy(), b.contextPolicy())
                || !Objects.equals(a.executionMode(), b.executionMode());
    }

    private SkillLocalizationService buildLocalizer() {
        try {
            Config config = Config.getInstance();
            // Reuse configured LLM timeout, with a safer floor for skill localization.
            HttpClientUtil httpClientUtil = new HttpClientUtil(
                    Duration.ofMillis(Math.max(45_000, config.getLlmApiTimeout()))
            );
            return new SkillLocalizationService(config, httpClientUtil);
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildPromptTemplate(String name, String description, String usageGuide) {
        String safeName = name != null ? name : "skill";
        String safeDescription = description != null ? description : "";
        String safeUsage = usageGuide != null ? usageGuide : "";
        if (safeUsage.length() > 3500) {
            safeUsage = safeUsage.substring(0, 3500);
        }
        return "你是一个专业技能执行器。技能名称: " + safeName + "\n"
                + "技能触发描述: " + safeDescription + "\n"
                + "请遵循技能使用说明完成任务:\n" + safeUsage + "\n\n"
                + "用户输入:\n{{input}}";
    }

    SkillManifest parseSkillMarkdown(String content, String defaultName, String defaultCategory) {
        return parseManifest(content, defaultName, defaultCategory);
    }

    SkillManifest applyCoreSkillProfile(SkillManifest manifest, String relPath) {
        if (manifest == null) {
            return null;
        }
        if (isDeploymentCoreSkill(manifest, relPath)) {
            List<String> toolIds = mergeDistinct(manifest.toolIds(), List.of("mcp-bridge"));
            List<String> toolGroups = mergeDistinct(manifest.toolGroups(), List.of("external-capability"));
            String contextPolicy = "advanced";
            SkillExecutionMode mode = SkillExecutionMode.MULTI;

            return new SkillManifest(
                    manifest.name(),
                    manifest.description(),
                    manifest.category(),
                    manifest.sourceLang(),
                    mode,
                    manifest.examples(),
                    manifest.usageGuide(),
                    strengthenDeploymentPrompt(manifest.promptTemplate()),
                    toolIds,
                    toolGroups,
                    contextPolicy
            );
        }
        if (isDocxCoreSkill(manifest, relPath)) {
            List<String> toolIds = mergeDistinct(manifest.toolIds(), List.of("docx-generator"));
            List<String> toolGroups = mergeDistinct(manifest.toolGroups(), List.of("document", "docx"));
            return new SkillManifest(
                    manifest.name(),
                    manifest.description(),
                    manifest.category(),
                    manifest.sourceLang(),
                    SkillExecutionMode.SINGLE,
                    manifest.examples(),
                    manifest.usageGuide(),
                    strengthenDocxPrompt(manifest.promptTemplate()),
                    toolIds,
                    toolGroups,
                    "standard"
            );
        }
        return manifest;
    }

    private SkillManifest parseManifest(String content, String defaultName, String defaultCategory) {
        if (content == null || content.isBlank()) {
            String fallbackName = (defaultName == null || defaultName.isBlank()) ? "skill" : defaultName;
            String normalizedName = normalizeSkillName(fallbackName);
            return new SkillManifest(
                    normalizedName,
                    normalizeDescription("Anthropic skill: " + normalizedName),
                    defaultCategory,
                    "en",
                    SkillExecutionMode.SINGLE,
                    List.of(),
                    "",
                    buildPromptTemplate(normalizedName, "Anthropic skill", ""),
                    List.of(),
                    List.of(),
                    "standard"
            );
        }

        String frontmatter = "";
        String body = content;
        if (content.startsWith("---")) {
            int end = content.indexOf("\n---", 3);
            if (end != -1) {
                frontmatter = content.substring(3, end).trim();
                int bodyStart = end + 4;
                if (bodyStart < content.length() && content.charAt(bodyStart) == '\n') {
                    bodyStart++;
                }
                body = content.substring(Math.min(bodyStart, content.length()));
            }
        }

        String name = getScalar(frontmatter, "name");
        if (name == null || name.isBlank()) {
            name = defaultName;
        }
        name = normalizeSkillName(name);

        String description = getScalar(frontmatter, "description");
        if (description == null || description.isBlank()) {
            description = "Anthropic skill: " + name;
        }
        description = normalizeDescription(description);

        String category = getScalar(frontmatter, "category");
        if (category == null || category.isBlank()) {
            category = defaultCategory;
        }

        String sourceLang = getScalar(frontmatter, "source_lang");
        if (sourceLang == null || sourceLang.isBlank()) {
            sourceLang = "en";
        }

        SkillExecutionMode mode = parseExecutionMode(getScalar(frontmatter, "execution_mode"));
        String usageGuide = body != null ? body.strip() : "";
        String promptTemplate = getScalar(frontmatter, "prompt_template");
        if (promptTemplate == null || promptTemplate.isBlank()) {
            promptTemplate = buildPromptTemplate(name, description, usageGuide);
        }

        List<String> examples = parseList(frontmatter, "examples");
        List<String> toolIds = parseList(frontmatter, "tool_ids");
        List<String> toolGroups = parseList(frontmatter, "tool_groups");
        if ((toolIds == null || toolIds.isEmpty()) && (toolGroups == null || toolGroups.isEmpty())) {
            toolGroups = List.of("llm-only");
        }
        String contextPolicy = getScalar(frontmatter, "context_policy");
        if (contextPolicy == null || contextPolicy.isBlank()) {
            contextPolicy = "standard";
        }

        return new SkillManifest(
                name,
                description,
                category,
                sourceLang,
                mode,
                examples,
                usageGuide,
                promptTemplate,
                toolIds,
                toolGroups,
                contextPolicy
        );
    }

    private boolean isDeploymentCoreSkill(SkillManifest manifest) {
        return isDeploymentCoreSkill(manifest, null);
    }

    private boolean isDeploymentCoreSkill(SkillManifest manifest, String relPath) {
        String name = manifest != null && manifest.name() != null ? manifest.name().toLowerCase(Locale.ROOT) : "";
        String desc = manifest != null && manifest.description() != null
                ? manifest.description().toLowerCase(Locale.ROOT)
                : "";
        String path = relPath != null ? relPath.toLowerCase(Locale.ROOT) : "";
        return name.contains("vercel-deploy")
                || name.contains("deploy-claimable")
                || path.contains("vercel-deploy")
                || path.contains("deploy-claimable")
                || desc.contains("deploy my app")
                || desc.contains("preview deployment");
    }

    private boolean isDocxCoreSkill(SkillManifest manifest, String relPath) {
        String name = manifest != null && manifest.name() != null ? manifest.name().toLowerCase(Locale.ROOT) : "";
        String desc = manifest != null && manifest.description() != null
                ? manifest.description().toLowerCase(Locale.ROOT)
                : "";
        String path = relPath != null ? relPath.toLowerCase(Locale.ROOT) : "";
        return name.contains("docx")
                || path.contains("/docx")
                || desc.contains("word")
                || desc.contains(".docx")
                || desc.contains("文档生成");
    }

    private List<String> mergeDistinct(List<String> left, List<String> right) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (left != null) {
            for (String v : left) {
                String normalized = normalizeToken(v);
                if (!normalized.isEmpty()) {
                    values.add(normalized);
                }
            }
        }
        if (right != null) {
            for (String v : right) {
                String normalized = normalizeToken(v);
                if (!normalized.isEmpty()) {
                    values.add(normalized);
                }
            }
        }
        return new ArrayList<>(values);
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String strengthenDeploymentPrompt(String promptTemplate) {
        String base = (promptTemplate == null || promptTemplate.isBlank())
                ? "Use available deployment tools to publish and return deployment URLs."
                : promptTemplate;
        String guidance = """
执行约束（部署型技能）：
1) 优先调用可用部署工具执行真实部署操作。
2) 如工具不可用或权限不足，明确告知原因与最小修复步骤（例如登录、令牌、项目权限）。
3) 输出必须包含：deployment_url（或 preview_url）、status、next_action。
""";
        if (base.contains("deployment_url") && base.contains("next_action")) {
            return base;
        }
        return guidance + "\n" + base;
    }

    private String strengthenDocxPrompt(String promptTemplate) {
        String base = (promptTemplate == null || promptTemplate.isBlank())
                ? "Generate a complete Word document based on the user request."
                : promptTemplate;
        String guidance = """
执行约束（文档交付技能）：
1) 优先调用 docx 工具直接生成可下载文档，不要只返回写作建议。
2) 若信息不完整，先用合理默认值完成首版文档，再补充“可选改进项”。
3) 最终输出必须包含：文档文件路径、文档标题、核心章节列表。
""";
        if (base.contains("文档文件路径") && base.contains("核心章节列表")) {
            return base;
        }
        return guidance + "\n" + base;
    }

    private String getScalar(String frontmatter, String key) {
        if (frontmatter == null || frontmatter.isBlank()) {
            return null;
        }
        String prefix = key + ":";
        for (String line : frontmatter.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                String value = trimmed.substring(prefix.length()).trim();
                return unquote(value);
            }
        }
        return null;
    }

    private List<String> parseList(String frontmatter, String key) {
        if (frontmatter == null || frontmatter.isBlank()) {
            return List.of();
        }
        String[] lines = frontmatter.split("\n");
        String header = key + ":";
        List<String> items = new ArrayList<>();
        boolean inList = false;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.trim();
            if (!inList) {
                if (!trimmed.startsWith(header)) {
                    continue;
                }
                String after = trimmed.substring(header.length()).trim();
                if (!after.isEmpty() && !after.equals("[]")) {
                    for (String token : after.split(",")) {
                        String value = unquote(token.trim());
                        if (!value.isBlank()) {
                            items.add(value);
                        }
                    }
                    return items;
                }
                inList = true;
                continue;
            }

            if (!raw.startsWith(" ") && !raw.startsWith("\t")) {
                break;
            }
            String item = trimmed;
            if (item.startsWith("-")) {
                item = item.substring(1).trim();
                item = unquote(item);
                if (!item.isBlank()) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private String unquote(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            if (v.length() >= 2) {
                v = v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    private SkillExecutionMode parseExecutionMode(String value) {
        if (value == null || value.isBlank()) {
            return SkillExecutionMode.SINGLE;
        }
        try {
            return SkillExecutionMode.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            return SkillExecutionMode.SINGLE;
        }
    }

    private String normalizeSkillName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "skill";
        }
        String value = raw.trim().toLowerCase()
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (value.isBlank()) {
            return "skill";
        }
        if (value.length() > 64) {
            return value.substring(0, 64);
        }
        return value;
    }

    private String normalizeDescription(String raw) {
        String value = (raw == null) ? "" : raw.trim();
        if (value.isBlank()) {
            value = "Skill description";
        }
        if (value.length() > 1024) {
            value = value.substring(0, 1024);
        }
        return value;
    }
}
