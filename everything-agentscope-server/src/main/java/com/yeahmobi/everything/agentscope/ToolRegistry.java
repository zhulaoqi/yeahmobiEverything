package com.yeahmobi.everything.agentscope;

import com.yeahmobi.everything.agentscope.tools.DocxGeneratorTool;
import com.yeahmobi.everything.agentscope.tools.CliBridgeTool;
import com.yeahmobi.everything.agentscope.tools.McpBridgeTool;
import com.yeahmobi.everything.agentscope.tools.WebResearchTool;
import com.yeahmobi.everything.agentscope.tools.WorkFollowupTool;
import com.yeahmobi.everything.common.Config;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Resolves runtime toolkit by normalized tool IDs / tool groups.
 * <p>
 * This keeps selection deterministic and skillId-driven instead of fuzzy
 * matching on skill name.
 * </p>
 */
public final class ToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolRegistry.class);

    private ToolRegistry() {
    }

    public static Toolkit resolve(String skillId,
                                  String skillName,
                                  List<String> toolIds,
                                  List<String> toolGroups) {
        List<String> ids = toolIds != null ? toolIds : List.of();
        List<String> groups = toolGroups != null ? toolGroups : List.of();

        if (contains(ids, "docx-generator")
                || contains(ids, "docx")
                || contains(groups, "document")
                || contains(groups, "docx")) {
            LOGGER.info("ToolRegistry resolved DocxGeneratorTool for skillId={}, skillName={}", skillId, skillName);
            return new DocxGeneratorTool();
        }

        if (contains(ids, "mcp-bridge")
                || contains(ids, "mcp")
                || contains(groups, "mcp")
                || contains(groups, "external-capability")) {
            if (!Config.getInstance().isAgentScopeMcpEnabled()) {
                LOGGER.info("ToolRegistry skip McpBridgeTool because MCP is disabled, skillId={}, skillName={}",
                        skillId, skillName);
                return new Toolkit();
            }
            LOGGER.info("ToolRegistry resolved McpBridgeTool for skillId={}, skillName={}", skillId, skillName);
            return new McpBridgeTool(Config.getInstance());
        }

        if (contains(ids, "web-research")
                || contains(ids, "web-search")
                || contains(groups, "web-search")
                || contains(groups, "information-retrieval")) {
            LOGGER.info("ToolRegistry resolved WebResearchTool for skillId={}, skillName={}", skillId, skillName);
            return new WebResearchTool();
        }

        if (contains(ids, "work-followup")
                || contains(ids, "todo-tracker")
                || contains(groups, "work-followup")
                || contains(groups, "personal-assistant")) {
            LOGGER.info("ToolRegistry resolved WorkFollowupTool for skillId={}, skillName={}", skillId, skillName);
            return new WorkFollowupTool();
        }

        if (contains(ids, "os-cli")
                || contains(ids, "os-scheduler")
                || contains(groups, "machine-ops")
                || contains(groups, "os-cli")
                || contains(groups, "os-scheduler")) {
            LOGGER.info("ToolRegistry resolved CliBridgeTool for skillId={}, skillName={}", skillId, skillName);
            return new CliBridgeTool(Config.getInstance());
        }

        // Backward compatible fallback for deployment skills that were imported
        // before tool metadata was normalized.
        if (isDeploymentSkill(skillId, skillName)) {
            if (!Config.getInstance().isAgentScopeMcpEnabled()) {
                LOGGER.info("ToolRegistry detected deployment skill but MCP disabled, skillId={}, skillName={}",
                        skillId, skillName);
                return new Toolkit();
            }
            LOGGER.info("ToolRegistry fallback to McpBridgeTool for deployment skillId={}, skillName={}",
                    skillId, skillName);
            return new McpBridgeTool(Config.getInstance());
        }

        // Backward compatible fallback (legacy skills without tool metadata)
        String lowerName = skillName != null ? skillName.toLowerCase() : "";
        if (lowerName.contains("文档") || lowerName.contains("docx")
                || lowerName.contains("word") || lowerName.contains("生成报告")) {
            LOGGER.info("ToolRegistry fallback to DocxGeneratorTool by legacy skillName, skillId={}, skillName={}",
                    skillId, skillName);
            return new DocxGeneratorTool();
        }

        if (lowerName.contains("检索")
                || lowerName.contains("搜索")
                || lowerName.contains("research")
                || lowerName.contains("知乎")
                || lowerName.contains("信息收集")) {
            LOGGER.info("ToolRegistry fallback to WebResearchTool by skillName, skillId={}, skillName={}",
                    skillId, skillName);
            return new WebResearchTool();
        }

        if (lowerName.contains("跟进")
                || lowerName.contains("待办")
                || lowerName.contains("提醒")
                || lowerName.contains("复盘")) {
            LOGGER.info("ToolRegistry fallback to WorkFollowupTool by skillName, skillId={}, skillName={}",
                    skillId, skillName);
            return new WorkFollowupTool();
        }

        if (lowerName.contains("cli")
                || lowerName.contains("终端")
                || lowerName.contains("命令行")
                || lowerName.contains("定时任务")
                || lowerName.contains("机器操作")) {
            LOGGER.info("ToolRegistry fallback to CliBridgeTool by skillName, skillId={}, skillName={}",
                    skillId, skillName);
            return new CliBridgeTool(Config.getInstance());
        }

        return new Toolkit();
    }

    private static boolean isDeploymentSkill(String skillId, String skillName) {
        String id = skillId != null ? skillId.toLowerCase() : "";
        String name = skillName != null ? skillName.toLowerCase() : "";
        return id.contains("vercel")
                || id.contains("deploy")
                || name.contains("vercel")
                || name.contains("deploy")
                || name.contains("preview deployment")
                || name.contains("claimable");
    }

    private static boolean contains(List<String> values, String target) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.trim().equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}

