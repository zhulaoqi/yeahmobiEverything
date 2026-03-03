package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.feedback.Feedback;
import com.yeahmobi.everything.knowledge.KnowledgeBaseService;
import com.yeahmobi.everything.knowledge.KnowledgeFile;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import com.yeahmobi.everything.repository.mysql.UserRepository;
import com.yeahmobi.everything.skill.AnthropicSkillImporter;
import com.yeahmobi.everything.skill.SkillImportReport;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link AdminService}.
 * <p>
 * Manages Skills via {@link SkillRepository} (MySQL) and invalidates
 * the Redis Skill cache via {@link CacheService} after every mutation.
 * Feedback operations delegate to {@link FeedbackRepository}.
 * </p>
 */
public class AdminServiceImpl implements AdminService {

    private static final Logger LOGGER = Logger.getLogger(AdminServiceImpl.class.getName());

    private final SkillRepository skillRepository;
    private final FeedbackRepository feedbackRepository;
    private final CacheService cacheService;
    private final SkillCommandParser commandParser;
    private final KnowledgeBaseService knowledgeBaseService;
    private final UserRepository userRepository;

    public AdminServiceImpl(SkillRepository skillRepository,
                            FeedbackRepository feedbackRepository,
                            CacheService cacheService,
                            UserRepository userRepository) {
        this(skillRepository, feedbackRepository, cacheService, new SkillCommandParser(), null, userRepository);
    }

    public AdminServiceImpl(SkillRepository skillRepository,
                            FeedbackRepository feedbackRepository,
                            CacheService cacheService) {
        this(skillRepository, feedbackRepository, cacheService, new SkillCommandParser(), null, null);
    }

    public AdminServiceImpl(SkillRepository skillRepository,
                            FeedbackRepository feedbackRepository,
                            CacheService cacheService,
                            KnowledgeBaseService knowledgeBaseService,
                            UserRepository userRepository) {
        this(skillRepository, feedbackRepository, cacheService, new SkillCommandParser(), knowledgeBaseService, userRepository);
    }

    public AdminServiceImpl(SkillRepository skillRepository,
                            FeedbackRepository feedbackRepository,
                            CacheService cacheService,
                            KnowledgeBaseService knowledgeBaseService) {
        this(skillRepository, feedbackRepository, cacheService, new SkillCommandParser(), knowledgeBaseService, null);
    }

    /**
     * Package-private constructor for testing with custom dependencies.
     */
    AdminServiceImpl(SkillRepository skillRepository,
                     FeedbackRepository feedbackRepository,
                     CacheService cacheService,
                     SkillCommandParser commandParser,
                     KnowledgeBaseService knowledgeBaseService,
                     UserRepository userRepository) {
        this.skillRepository = skillRepository;
        this.feedbackRepository = feedbackRepository;
        this.cacheService = cacheService;
        this.commandParser = commandParser;
        this.knowledgeBaseService = knowledgeBaseService;
        this.userRepository = userRepository;
    }

    @Override
    public List<SkillAdmin> getAllSkills() {
        return skillRepository.getAllSkills();
    }

    @Override
    public SkillIntegrationResult integrateSkill(String command) {
        try {
            SkillAdmin skill = commandParser.parse(command);
            skillRepository.saveSkill(skill);
            invalidateCache();
            return new SkillIntegrationResult(true, "Skill 集成成功", skill);
        } catch (IllegalArgumentException e) {
            return new SkillIntegrationResult(false, e.getMessage(), null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to integrate skill", e);
            return new SkillIntegrationResult(false, "Skill 集成失败: " + e.getMessage(), null);
        }
    }

    @Override
    public SkillIntegrationResult createSkillFromTemplate(SkillTemplate template) {
        // Validate required fields
        if (template.name() == null || template.name().isBlank()) {
            return new SkillIntegrationResult(false, "名称不能为空", null);
        }
        if (template.description() == null || template.description().isBlank()) {
            return new SkillIntegrationResult(false, "描述不能为空", null);
        }
        if (template.category() == null || template.category().isBlank()) {
            return new SkillIntegrationResult(false, "分类不能为空", null);
        }

        try {
            SkillExecutionMode mode = template.executionMode() != null
                    ? template.executionMode()
                    : SkillExecutionMode.SINGLE;
            List<String> toolIds = List.of();
            List<String> toolGroups = List.of();
            String contextPolicy = "default";
            String promptTemplate = template.promptTemplate() != null ? template.promptTemplate() : "";

            if (isInformationRetrievalSkill(template.name(), template.description(), template.category())) {
                toolIds = List.of("web-research");
                toolGroups = List.of("web-search", "information-retrieval");
                contextPolicy = "standard";
                promptTemplate = strengthenInformationRetrievalPrompt(promptTemplate, template.name());
            } else if (isWorkFollowupSkill(template.name(), template.description(), template.category())) {
                toolIds = List.of("work-followup");
                toolGroups = List.of("work-followup", "personal-assistant");
                contextPolicy = "standard";
                promptTemplate = strengthenWorkFollowupPrompt(promptTemplate, template.name());
            } else if (isCliMachineOpsSkill(template.name(), template.description(), template.category())) {
                toolIds = List.of("os-cli", "os-scheduler");
                toolGroups = List.of("machine-ops", "os-cli", "os-scheduler");
                contextPolicy = "standard";
                promptTemplate = strengthenCliMachineOpsPrompt(promptTemplate, template.name());
            } else if (isHrExecutionSkill(template.name(), template.description(), template.category())) {
                toolIds = List.of("work-followup");
                toolGroups = List.of("hr-assistant", "recruitment", "work-followup");
                contextPolicy = "standard";
                promptTemplate = strengthenHrExecutionPrompt(promptTemplate, template.name());
            }
            SkillAdmin skill = new SkillAdmin(
                    UUID.randomUUID().toString(),
                    template.name(),
                    template.description(),
                    template.icon() != null ? template.icon() : "default.png",
                    template.category(),
                    true,
                    "",
                    List.of(),
                    null,           // i18nJson
                    "admin",        // source
                    "zh",           // sourceLang
                    "basic",        // qualityTier
                    toolIds,
                    toolGroups,
                    contextPolicy,
                    template.type() != null ? template.type() : SkillType.GENERAL,
                    SkillKind.PROMPT_ONLY,
                    promptTemplate,
                    mode,
                    System.currentTimeMillis()
            );
            skillRepository.saveSkill(skill);
            invalidateCache();
            return new SkillIntegrationResult(true, "Skill 创建成功", skill);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create skill from template", e);
            return new SkillIntegrationResult(false, "Skill 创建失败: " + e.getMessage(), null);
        }
    }

    @Override
    public SkillIntegrationResult createKnowledgeSkill(KnowledgeSkillTemplate template) {
        // Validate required fields
        if (template.name() == null || template.name().isBlank()) {
            return new SkillIntegrationResult(false, "名称不能为空", null);
        }
        if (template.description() == null || template.description().isBlank()) {
            return new SkillIntegrationResult(false, "描述不能为空", null);
        }
        if (template.category() == null || template.category().isBlank()) {
            return new SkillIntegrationResult(false, "分类不能为空", null);
        }

        try {
            SkillExecutionMode mode = template.executionMode() != null
                    ? template.executionMode()
                    : SkillExecutionMode.SINGLE;
            String promptTemplate = (template.promptTemplate() != null && !template.promptTemplate().isBlank())
                    ? template.promptTemplate()
                    : defaultKnowledgePromptTemplate(template.name());
            SkillAdmin skill = new SkillAdmin(
                    UUID.randomUUID().toString(),
                    template.name(),
                    template.description(),
                    template.icon() != null ? template.icon() : "default.png",
                    template.category(),
                    true,
                    "",
                    List.of(),
                    null,           // i18nJson
                    "admin",        // source
                    "zh",           // sourceLang
                    "basic",        // qualityTier
                    List.of(),      // toolIds
                    List.of(),      // toolGroups
                    "default",      // contextPolicy
                    template.type() != null ? template.type() : SkillType.GENERAL,
                    SkillKind.KNOWLEDGE_RAG,
                    promptTemplate,
                    mode,
                    System.currentTimeMillis()
            );
            skillRepository.saveSkill(skill);

            // Bind knowledge files if provided
            if (knowledgeBaseService != null && template.knowledgeFileIds() != null) {
                for (String fileId : template.knowledgeFileIds()) {
                    knowledgeBaseService.bindFileToSkill(skill.id(), fileId);
                }
            }

            // Create manual knowledge entry if provided
            if (knowledgeBaseService != null
                    && template.manualKnowledgeContent() != null
                    && !template.manualKnowledgeContent().isBlank()) {
                KnowledgeFile manualFile = knowledgeBaseService.createFromManualInput(
                        skill.name() + " - 手动知识", template.manualKnowledgeContent());
                knowledgeBaseService.bindFileToSkill(skill.id(), manualFile.id());
            }

            invalidateCache();
            return new SkillIntegrationResult(true, "知识型 Skill 创建成功", skill);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create knowledge skill", e);
            return new SkillIntegrationResult(false, "知识型 Skill 创建失败: " + e.getMessage(), null);
        }
    }

    @Override
    public String renderPromptTemplate(String promptTemplate, String sampleInput) {
        if (promptTemplate == null) {
            return sampleInput != null ? sampleInput : "";
        }
        String rendered = promptTemplate.replace("{{user_input}}", sampleInput != null ? sampleInput : "");
        return rendered;
    }

    @Override
    public void toggleSkillStatus(String skillId, boolean enabled) {
        Optional<SkillAdmin> existing = skillRepository.getSkill(skillId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Skill not found: " + skillId);
        }
        SkillAdmin current = existing.get();
        SkillAdmin updated = new SkillAdmin(
                current.id(), current.name(), current.description(),
                current.icon(), current.category(), enabled,
                current.usageGuide(),
                current.examples(),
                current.i18nJson(),
                current.source(),
                current.sourceLang(),
                current.qualityTier(),
                current.toolIds(),
                current.toolGroups(),
                current.contextPolicy(),
                current.type(), current.kind(), current.promptTemplate(), current.executionMode(),
                current.createdAt()
        );
        skillRepository.updateSkill(updated);
        invalidateCache();
    }

    @Override
    public SkillIntegrationResult updateSkillDisplay(String skillId, String i18nJson, String qualityTier) {
        try {
            Optional<SkillAdmin> existing = skillRepository.getSkill(skillId);
            if (existing.isEmpty()) {
                return new SkillIntegrationResult(false, "Skill not found: " + skillId, null);
            }
            SkillAdmin current = existing.get();
            String nextI18n = (i18nJson != null && !i18nJson.isBlank()) ? i18nJson.trim() : null;
            String nextTier = (qualityTier != null && !qualityTier.isBlank()) ? qualityTier.trim() : current.qualityTier();

            SkillAdmin updated = new SkillAdmin(
                    current.id(),
                    current.name(),
                    current.description(),
                    current.icon(),
                    current.category(),
                    current.enabled(),
                    current.usageGuide(),
                    current.examples(),
                    nextI18n,
                    current.source(),
                    current.sourceLang(),
                    nextTier,
                    current.toolIds(),
                    current.toolGroups(),
                    current.contextPolicy(),
                    current.type(),
                    current.kind(),
                    current.promptTemplate(),
                    current.executionMode(),
                    current.createdAt()
            );
            skillRepository.updateSkill(updated);
            invalidateCache();
            return new SkillIntegrationResult(true, "展示字段已更新", updated);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update skill display: " + skillId, e);
            return new SkillIntegrationResult(false, "更新失败: " + e.getMessage(), null);
        }
    }

    @Override
    public List<Feedback> getAllFeedbacks() {
        return feedbackRepository.getAllFeedbacks();
    }

    @Override
    public void markFeedbackProcessed(String feedbackId) {
        long processedAt = System.currentTimeMillis();
        feedbackRepository.updateFeedbackStatus(feedbackId, "processed", processedAt);
    }

    @Override
    public String promoteUserToAdmin(String userIdOrEmail) {
        if (userRepository == null) {
            return "用户服务不可用";
        }
        if (userIdOrEmail == null || userIdOrEmail.isBlank()) {
            return "请输入用户ID或邮箱";
        }
        String value = userIdOrEmail.trim();
        try {
            if (value.contains("@")) {
                userRepository.promoteToAdminByEmail(value);
            } else {
                userRepository.promoteToAdminById(value);
            }
            return "已设置为管理员";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to promote user", e);
            return "设置失败: " + e.getMessage();
        }
    }

    @Override
    public int importAnthropicSkills(String repoPath) {
        return importSkillsFromPath(repoPath);
    }

    @Override
    public int importSkillsFromPath(String repoPath) {
        return importSkillsFromPathDetailed(repoPath).changedCount();
    }

    @Override
    public SkillImportReport importSkillsFromPathDetailed(String repoPath) {
        if (skillRepository == null) {
            return new SkillImportReport(false, "技能仓库不可用", 0, 0, 0, 0, 0, 0, 0, 0);
        }
        AnthropicSkillImporter importer = new AnthropicSkillImporter(skillRepository, cacheService);
        return importer.importFromPathDetailed(repoPath);
    }

    private void invalidateCache() {
        try {
            cacheService.invalidateSkillCache();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to invalidate skill cache", e);
        }
    }

    private String defaultKnowledgePromptTemplate(String skillName) {
        String displayName = (skillName == null || skillName.isBlank()) ? "企业知识问答助手" : skillName.trim();
        return """
你是“%s”。你必须优先基于系统提供的知识库内容回答问题。

回答规则：
1) 先给结论，再给依据。
2) 仅使用知识库中可验证的信息；若知识不足，明确写“知识库未命中或信息不足”，不要编造。
3) 回答末尾追加“知识来源”小节，列出你使用到的来源文件名。

用户问题：
{{input}}
""".formatted(displayName);
    }

    private boolean isInformationRetrievalSkill(String name, String description, String category) {
        String n = name != null ? name.toLowerCase() : "";
        String d = description != null ? description.toLowerCase() : "";
        String c = category != null ? category.toLowerCase() : "";
        return n.contains("检索")
                || n.contains("搜索")
                || n.contains("research")
                || n.contains("知乎")
                || d.contains("检索")
                || d.contains("搜索")
                || d.contains("信息收集")
                || c.contains("检索");
    }

    private String strengthenInformationRetrievalPrompt(String promptTemplate, String skillName) {
        String base = (promptTemplate == null || promptTemplate.isBlank())
                ? "请基于公开网页信息回答用户问题。"
                : promptTemplate;
        String displayName = (skillName == null || skillName.isBlank()) ? "信息检索秘书" : skillName.trim();
        String guidance = """
你是“%s”，必须先检索后回答。

回答规则：
1) 先给结论，再给证据。
2) 证据至少 2 条，并附可访问 URL。
3) 若证据不足或来源冲突，明确写“证据不足/存在冲突”，不要编造。
4) 最后给“下一步建议”。

用户问题：
{{input}}
""".formatted(displayName);
        if (base.contains("下一步建议") && base.contains("URL")) {
            return base;
        }
        return guidance + "\n" + base;
    }

    private boolean isWorkFollowupSkill(String name, String description, String category) {
        String n = name != null ? name.toLowerCase() : "";
        String d = description != null ? description.toLowerCase() : "";
        String c = category != null ? category.toLowerCase() : "";
        return n.contains("跟进")
                || n.contains("待办")
                || n.contains("提醒")
                || n.contains("复盘")
                || n.contains("todo")
                || d.contains("跟进")
                || d.contains("待办")
                || d.contains("提醒")
                || d.contains("复盘")
                || c.contains("跟进")
                || c.contains("效率");
    }

    private String strengthenWorkFollowupPrompt(String promptTemplate, String skillName) {
        String base = (promptTemplate == null || promptTemplate.isBlank())
                ? "请将用户需求拆解为可执行待办，并安排提醒与复盘。"
                : promptTemplate;
        String displayName = (skillName == null || skillName.isBlank()) ? "个人工作跟进秘书" : skillName.trim();
        String guidance = """
你是“%s”，必须先落地任务再给建议。

执行规则：
1) 对执行型请求（提醒/待办/跟进）必须先创建任务（createTodo）再返回建议。
2) 先产出待办清单（优先级 + 截止时间）并返回任务回执（如任务 ID/状态）。
3) 再给提醒计划（何时提醒、提醒内容）。
4) 最后给复盘框架（完成定义 + 复盘问题）。
5) 用户未给时间时，先用默认截止时间并明确提示用户可修改。
6) 禁止让用户“自己去设置提醒/自己去系统创建任务”。

用户问题：
{{input}}
""".formatted(displayName);
        if (base.contains("待办清单")
                && base.contains("提醒计划")
                && base.contains("复盘")
                && base.toLowerCase().contains("createtodo")
                && (base.contains("禁止让用户") || base.contains("不要让用户自行设置"))) {
            return base;
        }
        return guidance + "\n" + base;
    }

    private boolean isCliMachineOpsSkill(String name, String description, String category) {
        String n = name != null ? name.toLowerCase() : "";
        String d = description != null ? description.toLowerCase() : "";
        String c = category != null ? category.toLowerCase() : "";
        return n.contains("cli")
                || n.contains("终端")
                || n.contains("命令行")
                || n.contains("定时任务")
                || n.contains("机器操作")
                || d.contains("命令执行")
                || d.contains("定时")
                || d.contains("本机")
                || c.contains("自动化")
                || c.contains("运维")
                || c.contains("工具");
    }

    private String strengthenCliMachineOpsPrompt(String promptTemplate, String skillName) {
        String base = (promptTemplate == null || promptTemplate.isBlank())
                ? "请根据用户目标生成并执行本机 CLI 命令，支持定时任务。"
                : promptTemplate;
        String displayName = (skillName == null || skillName.isBlank()) ? "本机命令与定时任务助手" : skillName.trim();
        String guidance = """
你是“%s”，你的核心职责是安全执行本机命令并管理定时任务。

执行规则：
1) 每次执行前先识别操作系统（Windows/macOS/Linux）。
2) 默认先做 dry-run 预览并评估风险，再根据用户确认执行。
3) 对真实执行先申请确认票据（30秒有效），再执行命令。
4) 若用户要求定时任务，直接创建任务并回报结果（支持暂停、恢复、删除、立即执行）。
5) 命令执行失败时，给出可执行修复建议，不要只返回报错。
6) 面向非技术用户：禁止教程化输出，禁止“步骤1/步骤2”、禁止让用户输入技术参数（如 userConfirmed=true）。
7) 对用户仅输出三段：①我将帮你做什么 ②请确认一句话 ③结果与下一步。
8) 不在正文展开脚本细节，技术细节用于系统内部执行。

用户问题：
{{input}}
""".formatted(displayName);
        if (base.contains("dry-run") && base.contains("风险等级") && base.contains("定时任务")) {
            return base;
        }
        return guidance + "\n" + base;
    }

    private boolean isHrExecutionSkill(String name, String description, String category) {
        String n = name != null ? name.toLowerCase() : "";
        String d = description != null ? description.toLowerCase() : "";
        String c = category != null ? category.toLowerCase() : "";
        return n.contains("hr")
                || n.contains("招聘")
                || n.contains("候选人")
                || n.contains("面试")
                || n.contains("offer")
                || n.contains("入职")
                || n.contains("入转调离")
                || d.contains("招聘推进")
                || d.contains("面试结论")
                || d.contains("候选人")
                || c.contains("人事")
                || c.contains("招聘");
    }

    private String strengthenHrExecutionPrompt(String promptTemplate, String skillName) {
        String base = (promptTemplate == null || promptTemplate.isBlank())
                ? "请作为 HR 执行助手推进招聘与面试事务。"
                : promptTemplate;
        String displayName = (skillName == null || skillName.isBlank()) ? "HR执行助手" : skillName.trim();
        String guidance = """
你是“%s”，你的职责是把 HR 事务从“建议”推进到“可执行动作”。

执行规则：
1) 先判断意图：招聘推进 / 面试结论 / offer与入职 / 入转调离事务。
2) 输出必须是非技术三段式：
   - 【我将帮你做什么】一句话
   - 【请确认】一句话
   - 【结果】状态 + 下一步
3) 必须给出下一步动作（可直接转待办与提醒），不要停留在泛化建议。
4) 结论类输出必须带证据摘要（来自用户材料），不要无依据判断。
5) 若信息缺失，先给默认动作并提示用户补齐最小字段。

用户问题：
{{input}}
""".formatted(displayName);
        if (base.contains("三段式") && base.contains("下一步动作") && base.contains("证据")) {
            return base;
        }
        return guidance + "\n" + base;
    }
}
