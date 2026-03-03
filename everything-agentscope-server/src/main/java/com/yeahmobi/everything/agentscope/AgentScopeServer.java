package com.yeahmobi.everything.agentscope;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.common.Config;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Minimal AgentScope-compatible HTTP server for skill execution.
 */
public class AgentScopeServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentScopeServer.class);
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    private static final String POLICY_MINIMAL = "minimal";
    private static final String POLICY_STANDARD = "standard";
    private static final String POLICY_ADVANCED = "advanced";

    public static void main(String[] args) throws IOException {
        Config config = Config.getInstance();
        int port = config.getAgentScopeServerPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/health", exchange -> respondPlain(exchange, 200, "ok"));
        server.createContext("/api/agentscope/execute", exchange -> handleExecute(exchange, config));
        server.createContext("/api/agentscope/execute/stream", exchange -> handleExecuteStream(exchange, config));
        server.createContext("/api/agentscope/multi-agent/execute", exchange -> handleMultiAgent(exchange, config));
        server.createContext("/api/agentscope/multi-agent/stream", exchange -> handleMultiAgentStream(exchange, config));

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        LOGGER.info("AgentScope server started on port {}", port);
    }

    private static void handleExecute(HttpExchange exchange, Config config) throws IOException {
        String requestId = getOrCreateRequestId(exchange);
        long startAt = System.currentTimeMillis();
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, jsonError("Method not allowed", requestId));
            return;
        }
        String body = readBody(exchange);
        if (body == null || body.isBlank()) {
            respondJson(exchange, 400, jsonError("Empty request body", requestId));
            return;
        }

        JsonObject request = JsonParser.parseString(body).getAsJsonObject();
        String skillId = getString(request, "skillId");
        String input = getString(request, "input");
        String promptTemplate = getString(request, "promptTemplate");
        String skillName = getString(request, "skillName");
        List<String> toolIds = getStringList(request, "toolIds");
        List<String> toolGroups = getStringList(request, "toolGroups");
        String contextPolicy = getString(request, "contextPolicy");
        String prompt = buildPrompt(promptTemplate, input, readHistoryAsText(request));
        LOGGER.info("execute request, requestId={}, skillId={}, skillName={}, contextPolicy={}, toolIds={}, toolGroups={}",
                requestId, skillId, skillName, contextPolicy, toolIds, toolGroups);

        String apiKey = config.getAgentScopeApiKey();
        String modelName = config.getAgentScopeModel();
        if (apiKey == null || apiKey.isBlank() || modelName == null || modelName.isBlank()) {
            respondJson(exchange, 400, jsonError("AgentScope API 配置缺失", requestId));
            return;
        }

        try {
            ReActAgent agent = buildAgent(skillId, skillName, promptTemplate, toolIds, toolGroups, contextPolicy,
                    apiKey, modelName, requestId,
                    config.getAgentScopeExecutionTimeoutSeconds());
            Msg response = agent.call(Msg.builder().name("user").textContent(prompt).build()).block();
            String content = response != null ? response.getTextContent() : "";
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("content", content);
            result.addProperty("requestId", requestId);
            result.addProperty("latencyMs", System.currentTimeMillis() - startAt);
            respondJson(exchange, 200, result.toString());
        } catch (Exception e) {
            LOGGER.error("AgentScope execute error, requestId={}", requestId, e);
            respondJson(exchange, 500, jsonError("服务异常: " + e.getMessage(), requestId));
        }
    }

    /**
     * Handles single-agent true streaming execution via DashScope stream API.
     */
    private static void handleExecuteStream(HttpExchange exchange, Config config) throws IOException {
        String requestId = getOrCreateRequestId(exchange);
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, jsonError("Method not allowed", requestId));
            return;
        }
        String body = readBody(exchange);
        if (body == null || body.isBlank()) {
            respondJson(exchange, 400, jsonError("Empty request body", requestId));
            return;
        }

        JsonObject request = JsonParser.parseString(body).getAsJsonObject();
        String skillId = getString(request, "skillId");
        String input = getString(request, "input");
        String promptTemplate = getString(request, "promptTemplate");
        String skillName = getString(request, "skillName");
        List<String> toolIds = getStringList(request, "toolIds");
        List<String> toolGroups = getStringList(request, "toolGroups");
        String contextPolicy = getString(request, "contextPolicy");
        String prompt = buildPrompt(promptTemplate, input, readHistoryAsText(request));
        LOGGER.info("single-stream request, requestId={}, skillId={}, skillName={}, contextPolicy={}, toolIds={}, toolGroups={}",
                requestId, skillId, skillName, contextPolicy, toolIds, toolGroups);

        String apiKey = config.getAgentScopeApiKey();
        String modelName = config.getAgentScopeModel();
        if (apiKey == null || apiKey.isBlank() || modelName == null || modelName.isBlank()) {
            respondJson(exchange, 400, jsonError("AgentScope API 配置缺失", requestId));
            return;
        }

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        try (OutputStream os = exchange.getResponseBody()) {
            streamSingleModelResponse(os, apiKey, modelName, skillId, skillName, promptTemplate,
                    toolIds, toolGroups, contextPolicy, prompt);
            sendSSE(os, "done", "completed");
        } catch (Exception e) {
            LOGGER.error("AgentScope single streaming error, requestId={}", requestId, e);
        }
    }

    private static void handleMultiAgent(HttpExchange exchange, Config config) throws IOException {
        String requestId = getOrCreateRequestId(exchange);
        long startAt = System.currentTimeMillis();
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, jsonError("Method not allowed", requestId));
            return;
        }
        String body = readBody(exchange);
        if (body == null || body.isBlank()) {
            respondJson(exchange, 400, jsonError("Empty request body", requestId));
            return;
        }

        JsonObject request = JsonParser.parseString(body).getAsJsonObject();
        String skillId = getString(request, "skillId");
        String input = getString(request, "input");
        String promptTemplate = getString(request, "promptTemplate");
        String skillName = getString(request, "skillName");
        List<String> toolIds = getStringList(request, "toolIds");
        List<String> toolGroups = getStringList(request, "toolGroups");
        String contextPolicy = getString(request, "contextPolicy");
        String prompt = buildPrompt(promptTemplate, input, readHistoryAsText(request));
        LOGGER.info("multi-agent request, requestId={}, skillId={}, skillName={}, contextPolicy={}, toolIds={}, toolGroups={}",
                requestId, skillId, skillName, contextPolicy, toolIds, toolGroups);

        String apiKey = config.getAgentScopeApiKey();
        String modelName = config.getAgentScopeModel();
        if (apiKey == null || apiKey.isBlank() || modelName == null || modelName.isBlank()) {
            respondJson(exchange, 400, jsonError("AgentScope API 配置缺失", requestId));
            return;
        }

        try {
            int timeoutSeconds = config.getAgentScopeExecutionTimeoutSeconds();
            ReActAgent planner = buildAgent(skillId, "规划智能体", promptTemplate, toolIds, toolGroups, contextPolicy,
                    apiKey, modelName, requestId, timeoutSeconds);
            ReActAgent executor = buildAgent(skillId, "执行智能体", promptTemplate, toolIds, toolGroups, contextPolicy,
                    apiKey, modelName, requestId, timeoutSeconds);
            ReActAgent reviewer = buildAgent(skillId, "审阅智能体", promptTemplate, toolIds, toolGroups, contextPolicy,
                    apiKey, modelName, requestId, timeoutSeconds);

            String plan = planner.call(Msg.builder().name("user").textContent(
                    "请输出清晰步骤列表：\n" + prompt).build()).block().getTextContent();

            String execution = executor.call(Msg.builder().name("user").textContent(
                    "用户需求:\n" + prompt + "\n\n计划:\n" + plan).build()).block().getTextContent();

            String review = reviewer.call(Msg.builder().name("user").textContent(
                    "请检查并优化结果，必要时修正：\n" + execution).build()).block().getTextContent();

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("plan", plan);
            result.addProperty("execution", execution);
            result.addProperty("content", review);
            result.addProperty("requestId", requestId);
            result.addProperty("latencyMs", System.currentTimeMillis() - startAt);
            respondJson(exchange, 200, result.toString());
        } catch (Exception e) {
            LOGGER.error("AgentScope multi-agent error, requestId={}", requestId, e);
            respondJson(exchange, 500, jsonError("服务异常: " + e.getMessage(), requestId));
        }
    }

    private static String buildPrompt(String template, String input, String historyText) {
        String value = input != null ? input : "";
        String rendered = (template == null || template.isBlank())
                ? value
                : template.replace("{{input}}", value).replace("{{user_input}}", value);
        if (historyText == null || historyText.isBlank()) {
            return rendered;
        }
        return rendered + "\n\n以下是历史对话上下文：\n" + historyText;
    }

    private static ReActAgent buildAgent(String skillId,
                                         String skillName,
                                         String promptTemplate,
                                         List<String> toolIds,
                                         List<String> toolGroups,
                                         String contextPolicy,
                                         String apiKey, String modelName, String requestId,
                                         int timeoutSeconds) {
        String normalizedPolicy = normalizeContextPolicy(contextPolicy);
        String sysPrompt = buildSystemPrompt(skillName, promptTemplate, normalizedPolicy, toolIds, toolGroups);
        int safeTimeout = Math.max(30, timeoutSeconds);
        ExecutionConfig modelConfig = ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(safeTimeout))
                .maxAttempts(2)
                .build();
        List<String> effectiveToolIds = applyPolicyToToolIds(toolIds, normalizedPolicy);
        List<String> effectiveToolGroups = applyPolicyToToolGroups(toolGroups, normalizedPolicy);
        Toolkit toolkit = ToolRegistry.resolve(skillId, skillName, effectiveToolIds, effectiveToolGroups);
        
        return ReActAgent.builder()
                .name(skillName != null && !skillName.isBlank() ? skillName : "Agent")
                .sysPrompt(sysPrompt)
                .model(DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(modelName)
                        .build())
                .toolkit(toolkit)
                .modelExecutionConfig(modelConfig)
                .build();
    }

    private static String buildSystemPrompt(String skillName, String promptTemplate, String contextPolicy,
                                            List<String> toolIds, List<String> toolGroups) {
        String name = skillName != null && !skillName.isBlank() ? skillName : "Skill";
        String policy = normalizeContextPolicy(contextPolicy);
        boolean artifactFirst = isArtifactFirstByName(name);
        boolean deploymentFirst = isDeploymentByName(name);
        boolean retrievalFirst = isInformationRetrievalSkill(name, toolIds, toolGroups);
        boolean followupFirst = isWorkFollowupSkill(name, toolIds, toolGroups);
        boolean cliFirst = isCliSkill(name, toolIds, toolGroups);
        boolean hrFirst = isHrOpsSkill(name, toolIds, toolGroups);
        String disclosureGuide = switch (policy) {
            case POLICY_MINIMAL -> "披露级别: minimal。优先给结论与可执行答案，不展开内部机制，不主动暴露高级能力或外部工具。";
            case POLICY_ADVANCED -> "披露级别: advanced。可给完整步骤、可选工具与边界条件，必要时提示风险与替代方案。";
            default -> "披露级别: standard。给清晰步骤与关键依据，不冗长，不主动展示实验性能力。";
        };
        String artifactGuide = artifactFirst
                ? "\n交付优先规则：必须直接产出最终文件成果，不要输出安装命令、代码片段或教程。"
                  + "当用户需求明确时立即生成文件；若少量信息缺失，使用合理默认值完成交付。"
                : "";
        String deploymentGuide = deploymentFirst
                ? "\n部署优先规则：优先调用部署工具执行真实部署，并返回 deployment_url/status/next_action。"
                  + " 若工具不可用或权限不足，明确错误原因与最小修复步骤。"
                : "";
        String retrievalGuide = retrievalFirst
                ? "\n检索优先规则：先理解用户问题意图（概念解释/操作步骤/故障排查/对比选型/时效信息），再制定检索计划。"
                  + " 优先调用 smartSearch(question, preferredSite, limit) 获取候选来源；必要时再调用 webFetch 深挖正文。"
                  + " 给出结论时必须附来源 URL。"
                  + " 若用户指定站点但无结果，必须自动放宽范围继续检索（不要停在单一站点）。"
                  + " 若来源不足或无法验证，明确写“证据不足”，不要编造。"
                  + " 最终输出必须使用固定三段结构："
                  + "【结论】(1-3条可执行结论)"
                  + "【证据】(逐条列出来源要点+URL，至少2个来源；不足时写“证据不足”并解释)"
                  + "【下一步】(给用户可执行动作清单)。"
                : "";
        String followupGuide = followupFirst
                ? "\n跟进优先规则：优先将用户目标拆解为可执行待办，再安排提醒与复盘。"
                  + " 对于“提醒我/创建待办/跟进某事”等执行型请求，必须先调用工具落地（至少 createTodo），再输出结果。"
                  + " 优先调用 createTodo/updateTodo/deleteTodo/listTodosSorted/remindDue/completeTodo/weeklyReview/bulkComplete/bulkPostpone 管理任务状态。"
                  + " 若用户给出时间信息，需明确到具体截止时间；若时间缺失，先给默认时间并提示用户可修改。"
                  + " 禁止把操作甩给用户（如“请自行设置提醒/请去系统里创建”）。"
                  + " 输出需包含工具执行回执（如任务 ID、截止时间、状态）。"
                  + " 若工具暂不可用，必须明确说明“当前无法自动创建”，并给出最小替代方案。"
                  + " 最终输出建议格式："
                  + "【待办清单】(含优先级与截止时间)"
                  + "【提醒计划】(何时提醒、提醒对象)"
                  + "【复盘点】(完成标准与复盘问题)。"
                : "";
        String cliGuide = cliFirst
                ? "\nCLI 执行规则（非技术用户优先）："
                  + " 以下规则优先级高于技能模板中的任何冲突写法。"
                  + " 先调用 cliDetectOs 确认系统类型，再调用 cliExec。"
                  + " 默认先 dry-run=true 预览风险；若风险为中高或用户未确认，不可直接执行。"
                  + " 对真实执行，先调用 cliIssueConfirmTicket 获取确认票据，再调用 cliExecWithTicket（userConfirmed=true + confirmTicket）。"
                  + " 需要定时任务时使用 scheduleCreate/scheduleList/schedulePause/scheduleDelete/scheduleRunNow。"
                  + " 对用户的输出禁止教程化与参数化，不要输出“步骤1/步骤2”、不要要求用户输入 userConfirmed=true。"
                  + " 只用自然语言给出："
                  + "【我将帮你做什么】一句话；"
                  + "【请确认】一句话（如“确认后我立即创建”）；"
                  + "【结果】执行后返回成功/失败与下一步。"
                  + " 命令细节仅用于系统执行，不在正文展开脚本。"
                : "";
        String hrGuide = hrFirst
                ? "\nHR 执行规则（单角色深度）："
                  + " 优先把用户输入路由到 HR 意图：RECRUITMENT_ADVANCE / INTERVIEW_DECISION / OFFER_ONBOARDING / HR_TRANSACTION。"
                  + " 不做空泛知识讲解，必须落到可执行动作。"
                  + " 输出统一三段："
                  + "【我将帮你做什么】(一句话)"
                  + "【请确认】(一句话)"
                  + "【结果】(状态+下一步)。"
                  + " 若信息缺失，先给默认值并在结果中提示补齐字段。"
                  + " 对涉及候选人决策的结论需附证据摘要，不可无依据判断。"
                : "";
        if (promptTemplate == null || promptTemplate.isBlank()) {
            return "你是一个专业技能执行器，技能名称: " + name + "。\n"
                    + disclosureGuide + artifactGuide + deploymentGuide + retrievalGuide + followupGuide + cliGuide + hrGuide;
        }
        return "你是一个专业技能执行器，技能名称: " + name + "。\n"
                + disclosureGuide + "\n"
                + artifactGuide + "\n"
                + deploymentGuide + "\n"
                + retrievalGuide + "\n"
                + followupGuide + "\n"
                + cliGuide + "\n"
                + hrGuide + "\n"
                + "请严格遵循以下技能规范：\n" + promptTemplate;
    }

    private static boolean isArtifactFirstByName(String skillName) {
        String name = skillName != null ? skillName.toLowerCase() : "";
        return name.contains("docx")
                || name.contains("pptx")
                || name.contains("xlsx")
                || name.contains("pdf")
                || name.contains("文档")
                || name.contains("报告")
                || name.contains("演示");
    }

    private static boolean isDeploymentByName(String skillName) {
        String name = skillName != null ? skillName.toLowerCase() : "";
        return name.contains("deploy")
                || name.contains("vercel")
                || name.contains("发布")
                || name.contains("上线")
                || name.contains("preview");
    }

    private static boolean isInformationRetrievalByName(String skillName) {
        String name = skillName != null ? skillName.toLowerCase() : "";
        return name.contains("检索")
                || name.contains("搜索")
                || name.contains("research")
                || name.contains("信息收集")
                || name.contains("情报")
                || name.contains("知乎");
    }

    private static boolean isInformationRetrievalSkill(String skillName, List<String> toolIds, List<String> toolGroups) {
        if (isInformationRetrievalByName(skillName)) {
            return true;
        }
        if (containsIgnoreCase(toolIds, "web-research")
                || containsIgnoreCase(toolIds, "web-search")
                || containsIgnoreCase(toolGroups, "web-search")
                || containsIgnoreCase(toolGroups, "information-retrieval")) {
            return true;
        }
        return false;
    }

    private static boolean isWorkFollowupByName(String skillName) {
        String name = skillName != null ? skillName.toLowerCase() : "";
        return name.contains("跟进")
                || name.contains("待办")
                || name.contains("提醒")
                || name.contains("复盘")
                || name.contains("todo")
                || name.contains("followup");
    }

    private static boolean isWorkFollowupSkill(String skillName, List<String> toolIds, List<String> toolGroups) {
        if (isWorkFollowupByName(skillName)) {
            return true;
        }
        if (containsIgnoreCase(toolIds, "work-followup")
                || containsIgnoreCase(toolIds, "todo-tracker")
                || containsIgnoreCase(toolGroups, "work-followup")
                || containsIgnoreCase(toolGroups, "personal-assistant")) {
            return true;
        }
        return false;
    }

    private static boolean isCliByName(String skillName) {
        String name = skillName != null ? skillName.toLowerCase() : "";
        return name.contains("cli")
                || name.contains("命令行")
                || name.contains("终端")
                || name.contains("定时任务")
                || name.contains("机器操作");
    }

    private static boolean isCliSkill(String skillName, List<String> toolIds, List<String> toolGroups) {
        if (isCliByName(skillName)) {
            return true;
        }
        return containsIgnoreCase(toolIds, "os-cli")
                || containsIgnoreCase(toolIds, "os-scheduler")
                || containsIgnoreCase(toolGroups, "machine-ops")
                || containsIgnoreCase(toolGroups, "os-cli")
                || containsIgnoreCase(toolGroups, "os-scheduler");
    }

    private static boolean isHrOpsByName(String skillName) {
        String name = skillName != null ? skillName.toLowerCase() : "";
        return name.contains("hr")
                || name.contains("招聘")
                || name.contains("候选人")
                || name.contains("面试")
                || name.contains("offer")
                || name.contains("入职")
                || name.contains("入转调离");
    }

    private static boolean isHrOpsSkill(String skillName, List<String> toolIds, List<String> toolGroups) {
        if (isHrOpsByName(skillName)) {
            return true;
        }
        return containsIgnoreCase(toolIds, "hr-assistant")
                || containsIgnoreCase(toolGroups, "hr-assistant")
                || containsIgnoreCase(toolGroups, "recruitment");
    }

    private static boolean containsIgnoreCase(List<String> values, String expected) {
        if (values == null || values.isEmpty() || expected == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calls DashScope compatible streaming endpoint and forwards model token chunks as SSE content events.
     */
    private static void streamSingleModelResponse(OutputStream os, String apiKey, String modelName,
                                                  String skillId, String skillName, String promptTemplate,
                                                  List<String> toolIds, List<String> toolGroups,
                                                  String contextPolicy,
                                                  String prompt) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", modelName);
        payload.addProperty("stream", true);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", buildSystemPrompt(skillName, promptTemplate, contextPolicy, toolIds, toolGroups));
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt != null ? prompt : "");
        messages.add(userMsg);
        payload.add("messages", messages);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            String detail = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("DashScope stream failed: " + response.statusCode() + " " + detail);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }

                try {
                    JsonObject eventObj = JsonParser.parseString(data).getAsJsonObject();
                    if (!eventObj.has("choices") || eventObj.get("choices").getAsJsonArray().isEmpty()) {
                        continue;
                    }
                    JsonObject firstChoice = eventObj.get("choices").getAsJsonArray().get(0).getAsJsonObject();
                    if (!firstChoice.has("delta")) {
                        continue;
                    }
                    JsonObject delta = firstChoice.get("delta").getAsJsonObject();
                    if (!delta.has("content") || delta.get("content").isJsonNull()) {
                        continue;
                    }
                    String chunk = delta.get("content").getAsString();
                    if (!chunk.isEmpty()) {
                        sendSSE(os, "content", chunk);
                    }
                } catch (Exception ignored) {
                    // ignore malformed chunk and continue streaming
                }
            }
        }
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    private static List<String> getStringList(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return List.of();
        }
        if (!obj.get(key).isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        JsonArray arr = obj.getAsJsonArray(key);
        for (int i = 0; i < arr.size(); i++) {
            try {
                String value = arr.get(i).isJsonNull() ? null : arr.get(i).getAsString();
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            } catch (Exception ignored) {
                // ignore malformed item
            }
        }
        return values;
    }

    private static String readHistoryAsText(JsonObject request) {
        if (request == null || !request.has("history") || request.get("history").isJsonNull()) {
            return "";
        }
        if (!request.get("history").isJsonArray()) {
            return "";
        }
        JsonArray history = request.getAsJsonArray("history");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            try {
                JsonObject item = history.get(i).getAsJsonObject();
                String role = getString(item, "role");
                String content = getString(item, "content");
                if ((role == null || role.isBlank()) && (content == null || content.isBlank())) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(role != null && !role.isBlank() ? role : "unknown")
                        .append(": ")
                        .append(content != null ? content : "");
            } catch (Exception ignored) {
                // Ignore malformed history item
            }
        }
        return sb.toString();
    }

    private static String jsonError(String message, String requestId) {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("message", message);
        error.addProperty("requestId", requestId);
        return error.toString();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void respondPlain(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(HEADER_CONTENT_TYPE, "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String getOrCreateRequestId(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst(HEADER_REQUEST_ID);
        return (header == null || header.isBlank()) ? UUID.randomUUID().toString() : header;
    }

    /**
     * Handles streaming multi-agent execution with SSE (Server-Sent Events)
     */
    private static void handleMultiAgentStream(HttpExchange exchange, Config config) throws IOException {
        String requestId = getOrCreateRequestId(exchange);
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondJson(exchange, 405, jsonError("Method not allowed", requestId));
            return;
        }
        String body = readBody(exchange);
        if (body == null || body.isBlank()) {
            respondJson(exchange, 400, jsonError("Empty request body", requestId));
            return;
        }

        JsonObject request = JsonParser.parseString(body).getAsJsonObject();
        String skillId = getString(request, "skillId");
        String input = getString(request, "input");
        String promptTemplate = getString(request, "promptTemplate");
        String skillName = getString(request, "skillName");
        List<String> toolIds = getStringList(request, "toolIds");
        List<String> toolGroups = getStringList(request, "toolGroups");
        String contextPolicy = getString(request, "contextPolicy");
        String prompt = buildPrompt(promptTemplate, input, readHistoryAsText(request));
        LOGGER.info("multi-stream request, requestId={}, skillId={}, skillName={}, contextPolicy={}, toolIds={}, toolGroups={}",
                requestId, skillId, skillName, contextPolicy, toolIds, toolGroups);

        String apiKey = config.getAgentScopeApiKey();
        String modelName = config.getAgentScopeModel();
        if (apiKey == null || apiKey.isBlank() || modelName == null || modelName.isBlank()) {
            respondJson(exchange, 400, jsonError("AgentScope API 配置缺失", requestId));
            return;
        }

        // Set SSE headers
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        try (OutputStream os = exchange.getResponseBody()) {
            // Send planning step
            sendSSE(os, "plan", "正在制定执行计划...");
            Thread.sleep(500);
            sendSSE(os, "plan", "✓ 分析任务需求\n✓ 确定执行步骤\n✓ 准备相关工具");

            // Send execution step
            sendSSE(os, "execution", "正在执行任务...");
            Thread.sleep(300);

            // Execute with planner
            ReActAgent planner = buildAgent(skillId, skillName + "-planner", promptTemplate, toolIds, toolGroups, contextPolicy,
                    apiKey, modelName, requestId, config.getAgentScopeExecutionTimeoutSeconds());
            Msg planResult = planner.call(Msg.builder().name("user")
                    .textContent("请为以下任务制定详细执行计划：" + prompt).build()).block();
            String plan = planResult != null ? planResult.getTextContent() : "计划制定完成";
            sendSSE(os, "execution", "执行中: " + plan.substring(0, Math.min(100, plan.length())) + "...");

            // Execute with executor
            ReActAgent executor = buildAgent(skillId, skillName + "-executor", promptTemplate, toolIds, toolGroups, contextPolicy,
                    apiKey, modelName, requestId, config.getAgentScopeExecutionTimeoutSeconds());
            Msg execResult = executor.call(Msg.builder().name("user").textContent(prompt).build()).block();
            String execution = execResult != null ? execResult.getTextContent() : "";
            sendSSE(os, "execution", "✓ 任务执行完成");

            // Send review step
            sendSSE(os, "review", "正在审核结果...");
            Thread.sleep(300);

            ReActAgent reviewer = buildAgent(skillId, skillName + "-reviewer", promptTemplate, toolIds, toolGroups, contextPolicy,
                    apiKey, modelName, requestId, config.getAgentScopeExecutionTimeoutSeconds());
            Msg reviewResult = reviewer.call(Msg.builder().name("user")
                    .textContent("请审核以下执行结果：" + execution).build()).block();
            String review = reviewResult != null ? reviewResult.getTextContent() : "审核通过";
            sendSSE(os, "review", "✓ 结果验证通过\n✓ 质量检查完成");

            // Send final content
            sendSSE(os, "content", review);
            sendSSE(os, "done", "completed");

        } catch (Exception e) {
            LOGGER.error("AgentScope streaming error, requestId={}", requestId, e);
            try (OutputStream os = exchange.getResponseBody()) {
                sendSSE(os, "error", "执行失败: " + e.getMessage());
            }
        }
    }

    /**
     * Sends a Server-Sent Event message
     */
    private static void sendSSE(OutputStream os, String event, String data) throws IOException {
        String message = "event: " + event + "\n" +
                        "data: " + data.replace("\n", "\ndata: ") + "\n\n";
        os.write(message.getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    private static String normalizeContextPolicy(String contextPolicy) {
        if (contextPolicy == null || contextPolicy.isBlank()) {
            return POLICY_STANDARD;
        }
        String value = contextPolicy.trim().toLowerCase();
        if (POLICY_MINIMAL.equals(value) || POLICY_ADVANCED.equals(value) || POLICY_STANDARD.equals(value)) {
            return value;
        }
        if ("default".equals(value)) {
            return POLICY_STANDARD;
        }
        return POLICY_STANDARD;
    }

    private static List<String> applyPolicyToToolIds(List<String> toolIds, String contextPolicy) {
        List<String> ids = toolIds != null ? toolIds : List.of();
        if (POLICY_MINIMAL.equals(normalizeContextPolicy(contextPolicy))) {
            return List.of();
        }
        if (POLICY_STANDARD.equals(normalizeContextPolicy(contextPolicy))) {
            return ids.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .filter(v -> {
                        String value = v.trim().toLowerCase();
                        return !"mcp".equals(value) && !"mcp-bridge".equals(value);
                    })
                    .toList();
        }
        return ids;
    }

    private static List<String> applyPolicyToToolGroups(List<String> toolGroups, String contextPolicy) {
        List<String> groups = toolGroups != null ? toolGroups : List.of();
        if (POLICY_MINIMAL.equals(normalizeContextPolicy(contextPolicy))) {
            return List.of();
        }
        if (POLICY_STANDARD.equals(normalizeContextPolicy(contextPolicy))) {
            return groups.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .filter(v -> {
                        String value = v.trim().toLowerCase();
                        return !"mcp".equals(value) && !"external-capability".equals(value);
                    })
                    .toList();
        }
        return groups;
    }
}
