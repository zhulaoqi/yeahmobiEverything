package com.yeahmobi.everything.chat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.common.NetworkException;
import com.yeahmobi.everything.knowledge.KnowledgeBaseService;
import com.yeahmobi.everything.knowledge.KnowledgeFile;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.ChatRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Implementation of {@link ChatService}.
 * <p>
 * Constructs LLM API requests with Skill context and optional knowledge base
 * content (for KNOWLEDGE_RAG skills). Uses {@link CompletableFuture} for
 * asynchronous execution with a 30-second timeout.
 * </p>
 */
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /** Default timeout for LLM API calls in seconds. */
    static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /** Default TTL for knowledge text cache in seconds (30 minutes). */
    static final long KNOWLEDGE_CACHE_TTL_SECONDS = 1800;
    static final int MAX_KNOWLEDGE_CONTEXT_CHARS = 12000;
    static final int MAX_KNOWLEDGE_SOURCES = 6;

    private final ChatRepository chatRepository;
    private final CacheService cacheService;
    private final SkillRepository skillRepository;
    private final HttpClientUtil httpClientUtil;
    private final Config config;
    private final KnowledgeBaseService knowledgeBaseService;
    private final Gson gson;

    /**
     * Creates a ChatServiceImpl with all required dependencies.
     *
     * @param chatRepository  the local chat repository for message/session persistence
     * @param cacheService    the Redis cache service for knowledge text caching
     * @param skillRepository the MySQL skill repository for Skill configuration
     * @param httpClientUtil  the HTTP client utility for LLM API calls
     * @param config          the application configuration
     */
    public ChatServiceImpl(ChatRepository chatRepository,
                           CacheService cacheService,
                           SkillRepository skillRepository,
                           HttpClientUtil httpClientUtil,
                           Config config) {
        this(chatRepository, cacheService, skillRepository, httpClientUtil, config, null);
    }

    public ChatServiceImpl(ChatRepository chatRepository,
                           CacheService cacheService,
                           SkillRepository skillRepository,
                           HttpClientUtil httpClientUtil,
                           Config config,
                           KnowledgeBaseService knowledgeBaseService) {
        this.chatRepository = chatRepository;
        this.cacheService = cacheService;
        this.skillRepository = skillRepository;
        this.httpClientUtil = httpClientUtil;
        this.config = config;
        this.knowledgeBaseService = knowledgeBaseService;
        this.gson = new Gson();
    }

    @Override
    public CompletableFuture<ChatResponse> sendMessage(String skillId, String userMessage, List<ChatMessage> history) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Fetch Skill configuration
                Optional<SkillAdmin> skillOpt = skillRepository.getSkill(skillId);
                String promptTemplate = skillOpt.map(SkillAdmin::promptTemplate).orElse("");
                SkillKind skillKind = skillOpt.map(SkillAdmin::kind).orElse(SkillKind.PROMPT_ONLY);
                SkillExecutionMode executionMode = skillOpt.map(SkillAdmin::executionMode)
                        .orElse(SkillExecutionMode.SINGLE);
                String skillName = skillOpt.map(SkillAdmin::name).orElse("Skill");
                List<String> toolIds = skillOpt.map(SkillAdmin::toolIds).orElse(List.of());
                List<String> toolGroups = skillOpt.map(SkillAdmin::toolGroups).orElse(List.of());
                String contextPolicy = skillOpt.map(SkillAdmin::contextPolicy).orElse("default");
                boolean artifactFirst = isArtifactFirstSkill(skillName, toolIds, toolGroups);
                if (artifactFirst) {
                    executionMode = SkillExecutionMode.SINGLE;
                }
                String effectivePromptTemplate = adaptPromptTemplateForArtifactFirst(skillName, promptTemplate, artifactFirst);

                // 2. Build context with knowledge for KNOWLEDGE_RAG skills
                String contextMessage;
                if (skillKind == SkillKind.KNOWLEDGE_RAG) {
                    contextMessage = buildContextWithKnowledge(skillId, userMessage);
                } else {
                    contextMessage = userMessage;
                }

                // 3. Construct AgentScope request JSON
                String requestBody = buildAgentScopeRequestJson(
                        skillId,
                        effectivePromptTemplate,
                        contextMessage,
                        skillName,
                        history,
                        toolIds,
                        toolGroups,
                        contextPolicy
                );

                // 4. Send request to AgentScope Server
                String apiUrl = resolveAgentScopeUrl(executionMode);
                if (apiUrl == null || apiUrl.isBlank()) {
                    return new ChatResponse(null, null, null, false, "AgentScope 服务未配置");
                }

                // Log the actual URL for debugging
                log.debug("Sending request to AgentScope: {}", apiUrl);
                log.debug("Timeout: {}s", getTimeoutSeconds());

                Map<String, String> headers = Map.of("Content-Type", "application/json");
                String responseBody = httpClientUtil.post(apiUrl, requestBody, headers);

                // 5. Parse AgentScope response
                return parseAgentScopeResponse(responseBody);

            } catch (Exception e) {
                return new ChatResponse(null, null, null, false, "服务不可用: " + e.getMessage());
            }
        }).orTimeout(getTimeoutSeconds(), TimeUnit.SECONDS)
          .exceptionally(ex -> {
              if (ex instanceof TimeoutException || (ex.getCause() instanceof TimeoutException)) {
                  return new ChatResponse(null, null, null, false, "请求超时，请稍后重试");
              }
              return new ChatResponse(null, null, null, false, "服务不可用: " + ex.getMessage());
          });
    }

    @Override
    public CompletableFuture<ChatResponse> sendMessageStream(
            String skillId,
            String userMessage,
            List<ChatMessage> history,
            Consumer<String> onDelta
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Fetch Skill configuration
                Optional<SkillAdmin> skillOpt = skillRepository.getSkill(skillId);
                String promptTemplate = skillOpt.map(SkillAdmin::promptTemplate).orElse("");
                SkillKind skillKind = skillOpt.map(SkillAdmin::kind).orElse(SkillKind.PROMPT_ONLY);
                SkillExecutionMode executionMode = skillOpt.map(SkillAdmin::executionMode)
                        .orElse(SkillExecutionMode.SINGLE);
                String skillName = skillOpt.map(SkillAdmin::name).orElse("Skill");
                List<String> toolIds = skillOpt.map(SkillAdmin::toolIds).orElse(List.of());
                List<String> toolGroups = skillOpt.map(SkillAdmin::toolGroups).orElse(List.of());
                String contextPolicy = skillOpt.map(SkillAdmin::contextPolicy).orElse("default");
                boolean artifactFirst = isArtifactFirstSkill(skillName, toolIds, toolGroups);
                if (artifactFirst) {
                    executionMode = SkillExecutionMode.SINGLE;
                }
                String effectivePromptTemplate = adaptPromptTemplateForArtifactFirst(skillName, promptTemplate, artifactFirst);

                // 2. Build context with knowledge for KNOWLEDGE_RAG skills
                String contextMessage;
                if (skillKind == SkillKind.KNOWLEDGE_RAG) {
                    contextMessage = buildContextWithKnowledge(skillId, userMessage);
                } else {
                    contextMessage = userMessage;
                }

                // 3. Construct AgentScope request JSON
                String requestBody = buildAgentScopeRequestJson(
                        skillId,
                        effectivePromptTemplate,
                        contextMessage,
                        skillName,
                        history,
                        toolIds,
                        toolGroups,
                        contextPolicy
                );

                // 4. Use real streaming endpoints
                if (executionMode == SkillExecutionMode.MULTI) {
                    // MULTI mode streaming endpoint
                    return sendMessageWithStreaming(requestBody, onDelta);
                } else {
                    // SINGLE mode true streaming endpoint
                    return sendMessageWithSingleStreaming(requestBody, onDelta);
                }
            } catch (Exception e) {
                return new ChatResponse(null, null, null, false, "服务不可用: " + e.getMessage());
            }
        }).orTimeout(getTimeoutSeconds() * 2, TimeUnit.SECONDS) // Double timeout for streaming
          .exceptionally(ex -> {
              if (ex instanceof TimeoutException || (ex.getCause() instanceof TimeoutException)) {
                  return new ChatResponse(null, null, null, false, "请求超时，请稍后重试");
              }
              return new ChatResponse(null, null, null, false, "服务不可用: " + ex.getMessage());
          });
    }

    /**
     * Sends a message using SSE streaming and processes events in real-time.
     */
    private ChatResponse sendMessageWithStreaming(String requestBody, Consumer<String> onDelta) {
        String baseUrl = config.getAgentScopeServerUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:" + config.getAgentScopeServerPort();
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        String streamUrl = url + "/api/agentscope/multi-agent/stream";

        StringBuilder contentBuffer = new StringBuilder();
        String[] plan = {null};
        String[] execution = {null};
        boolean[] hasError = {false};
        String[] errorMessage = {""};

        try {
            Map<String, String> headers = Map.of("Content-Type", "application/json");
            httpClientUtil.postStream(streamUrl, requestBody, headers, (event, data) -> {
                // Process different SSE event types
                switch (event) {
                    case "plan":
                        plan[0] = data;
                        if (onDelta != null) {
                            onDelta.accept("📋 " + data + "\n\n");
                        }
                        break;
                    case "execution":
                        execution[0] = data;
                        if (onDelta != null) {
                            onDelta.accept("⚙️ " + data + "\n\n");
                        }
                        break;
                    case "review":
                        if (onDelta != null) {
                            onDelta.accept("✅ " + data + "\n\n");
                        }
                        break;
                    case "content":
                        contentBuffer.append(data);
                        if (onDelta != null) {
                            onDelta.accept(data);
                        }
                        break;
                    case "error":
                        hasError[0] = true;
                        errorMessage[0] = data;
                        break;
                    case "done":
                        // Stream completed
                        break;
                    default:
                        // Unknown event type, ignore
                        break;
                }
            });

            if (hasError[0]) {
                return new ChatResponse(null, null, null, false, errorMessage[0]);
            }

            return new ChatResponse(
                    contentBuffer.toString(),
                    plan[0],
                    execution[0],
                    true,
                    null
            );
        } catch (NetworkException e) {
            return new ChatResponse(null, null, null, false, "流式请求失败: " + e.getMessage());
        }
    }

    /**
     * Sends a message to SINGLE-mode real streaming endpoint and forwards token chunks.
     */
    private ChatResponse sendMessageWithSingleStreaming(String requestBody, Consumer<String> onDelta) {
        String streamUrl = resolveSingleStreamUrl();
        if (streamUrl == null || streamUrl.isBlank()) {
            return new ChatResponse(null, null, null, false, "AgentScope 单模型流式服务未配置");
        }

        StringBuilder contentBuffer = new StringBuilder();
        boolean[] hasError = {false};
        String[] errorMessage = {""};

        try {
            Map<String, String> headers = Map.of("Content-Type", "application/json");
            httpClientUtil.postStream(streamUrl, requestBody, headers, (event, data) -> {
                if ("content".equals(event)) {
                    contentBuffer.append(data);
                    if (onDelta != null) {
                        onDelta.accept(data);
                    }
                } else if ("error".equals(event)) {
                    hasError[0] = true;
                    errorMessage[0] = data;
                }
            });

            if (hasError[0]) {
                return new ChatResponse(null, null, null, false, errorMessage[0]);
            }
            return new ChatResponse(contentBuffer.toString(), null, null, true, null);
        } catch (NetworkException e) {
            return new ChatResponse(null, null, null, false, "流式请求失败: " + e.getMessage());
        }
    }

    @Override
    public List<ChatMessage> getChatHistory(String skillId) {
        return chatRepository.getHistory(skillId);
    }

    @Override
    public void saveMessage(ChatMessage message) {
        chatRepository.saveMessage(message);
    }

    @Override
    public void saveMessage(ChatMessage message, String userId, String skillName) {
        chatRepository.saveMessage(message, userId, skillName);
    }

    @Override
    public void clearHistory(String sessionId) {
        chatRepository.clearHistory(sessionId);
    }

    @Override
    public String getRecentSessionForSkill(String skillId, String userId) {
        List<ChatSession> sessions = chatRepository.getAllSessions(userId);
        return sessions.stream()
                .filter(s -> skillId.equals(s.skillId()))
                .findFirst()
                .map(ChatSession::id)
                .orElse(null);
    }

    @Override
    public List<ChatSession> getAllSessions(String userId) {
        return chatRepository.getAllSessions(userId);
    }

    @Override
    public List<ChatSession> searchSessions(String keyword, String userId) {
        return chatRepository.searchSessions(keyword, userId);
    }

    @Override
    public void deleteSession(String sessionId) {
        chatRepository.deleteSession(sessionId);
    }

    @Override
    public String buildContextWithKnowledge(String skillId, String userMessage) {
        // 1. Try to get knowledge text from Redis cache
        Optional<String> cachedText = Optional.empty();
        try {
            cachedText = cacheService.getCachedKnowledgeText(skillId);
        } catch (Exception e) {
            // Redis unavailable — fall through to database query
        }

        String knowledgeText;
        if (cachedText.isPresent() && !cachedText.get().isEmpty()) {
            knowledgeText = cachedText.get();
        } else {
            // 2. Fetch knowledge text from database via Skill repository
            knowledgeText = fetchKnowledgeTextFromDatabase(skillId);

            // 3. Cache the result if non-empty
            if (knowledgeText != null && !knowledgeText.isEmpty()) {
                try {
                    cacheService.cacheKnowledgeText(skillId, knowledgeText, KNOWLEDGE_CACHE_TTL_SECONDS);
                } catch (Exception e) {
                    // Redis unavailable — continue without caching
                }
            }
        }

        List<String> sources = fetchKnowledgeSourcesFromDatabase(skillId);

        // 4. Build the combined context string
        if (knowledgeText != null && !knowledgeText.isEmpty()) {
            String prioritizedKnowledge = prioritizeKnowledge(knowledgeText, userMessage, MAX_KNOWLEDGE_CONTEXT_CHARS);
            return """
【知识库回答规则】
1) 仅基于下方知识库内容作答；若知识不足，明确说明“知识库未命中”，不要编造。
2) 回答优先给出结论，再给关键依据。
3) 在回答末尾添加“知识来源”并列出已命中的文件名。

【知识来源】
%s

【知识库内容】
%s

【用户问题】
%s
""".formatted(renderKnowledgeSources(sources), prioritizedKnowledge, userMessage != null ? userMessage : "");
        }
        return """
【知识库回答规则】
当前未检索到可用知识内容，请明确告知用户“知识库未命中或尚未绑定资料”，不要编造事实。

【用户问题】
%s
""".formatted(userMessage != null ? userMessage : "");
    }

    // ---- Internal helpers ----

    /**
     * Builds the JSON request body for AgentScope server.
     */
    String buildAgentScopeRequestJson(String skillId,
                                      String promptTemplate,
                                      String userMessage,
                                      String skillName,
                                      List<ChatMessage> history) {
        return buildAgentScopeRequestJson(
                skillId, promptTemplate, userMessage, skillName, history, List.of(), List.of(), "default"
        );
    }

    String buildAgentScopeRequestJson(String skillId,
                                      String promptTemplate,
                                      String userMessage,
                                      String skillName,
                                      List<ChatMessage> history,
                                      List<String> toolIds,
                                      List<String> toolGroups,
                                      String contextPolicy) {
        JsonObject request = new JsonObject();
        request.addProperty("skillId", skillId != null ? skillId : "");
        request.addProperty("input", userMessage != null ? userMessage : "");
        request.addProperty("promptTemplate", promptTemplate != null ? promptTemplate : "");
        request.addProperty("skillName", skillName != null ? skillName : "Skill");
        request.add("history", buildHistoryJson(history));
        request.add("toolIds", buildStringArray(toolIds));
        request.add("toolGroups", buildStringArray(toolGroups));
        request.addProperty("contextPolicy", contextPolicy != null ? contextPolicy : "default");
        JsonObject userContext = new JsonObject();
        userContext.addProperty("channel", "desktop");
        userContext.addProperty("app", "yeahmobi-everything");
        request.add("userContext", userContext);
        return gson.toJson(request);
    }

    private JsonArray buildHistoryJson(List<ChatMessage> history) {
        JsonArray arr = new JsonArray();
        if (history == null || history.isEmpty()) {
            return arr;
        }
        for (ChatMessage msg : history) {
            if (msg == null) {
                continue;
            }
            JsonObject item = new JsonObject();
            item.addProperty("role", msg.role() != null ? msg.role() : "");
            item.addProperty("content", msg.content() != null ? msg.content() : "");
            item.addProperty("timestamp", msg.timestamp());
            arr.add(item);
        }
        return arr;
    }

    private JsonArray buildStringArray(List<String> values) {
        JsonArray arr = new JsonArray();
        if (values == null || values.isEmpty()) {
            return arr;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            arr.add(value.trim());
        }
        return arr;
    }

    ChatResponse parseAgentScopeResponse(String responseBody) {
        try {
            JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
            boolean success = response.has("success") && response.get("success").getAsBoolean();
            if (!success) {
                String message = response.has("message") ? response.get("message").getAsString() : "服务异常";
                return new ChatResponse(null, null, null, false, message);
            }
            String content = getOptionalString(response, "content", "");
            String plan = getOptionalString(response, "plan", null);
            String execution = getOptionalString(response, "execution", null);
            return new ChatResponse(content, plan, execution, true, null);
        } catch (Exception e) {
            return new ChatResponse(null, null, null, false, "响应解析失败: " + e.getMessage());
        }
    }

    private String getOptionalString(JsonObject obj, String field, String defaultValue) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(field).getAsString();
    }

    /**
     * Fetches knowledge text from the database for a given Skill.
     * <p>
     * This queries the Skill's prompt template as a fallback. When the
     * KnowledgeBaseService (task 8.1) is implemented, this method should
     * be updated to use getMergedKnowledgeText from that service.
     * </p>
     *
     * @param skillId the Skill ID
     * @return the knowledge text, or empty string if not found
     */
    String fetchKnowledgeTextFromDatabase(String skillId) {
        if (knowledgeBaseService == null || skillId == null || skillId.isBlank()) {
            return "";
        }
        try {
            String merged = knowledgeBaseService.getMergedKnowledgeText(skillId);
            return merged != null ? merged : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<String> fetchKnowledgeSourcesFromDatabase(String skillId) {
        if (knowledgeBaseService == null || skillId == null || skillId.isBlank()) {
            return List.of();
        }
        try {
            return knowledgeBaseService.getFilesForSkill(skillId).stream()
                    .map(KnowledgeFile::fileName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .limit(MAX_KNOWLEDGE_SOURCES)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String renderKnowledgeSources(List<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return "- （未记录来源文件）";
        }
        StringBuilder sb = new StringBuilder();
        for (String source : sources) {
            sb.append("- ").append(source).append("\n");
        }
        return sb.toString().trim();
    }

    private String prioritizeKnowledge(String knowledgeText, String userMessage, int maxChars) {
        if (knowledgeText == null || knowledgeText.isBlank()) {
            return "";
        }
        if (maxChars <= 0 || knowledgeText.length() <= maxChars) {
            return knowledgeText;
        }
        List<String> chunks = splitKnowledgeChunks(knowledgeText);
        if (chunks.isEmpty()) {
            return knowledgeText.substring(0, Math.min(maxChars, knowledgeText.length()));
        }
        List<String> keywords = extractKeywords(userMessage);
        StringBuilder selected = new StringBuilder();
        boolean[] used = new boolean[chunks.size()];

        int priorityBudget = (int) (maxChars * 0.7);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            if (!matchesAnyKeyword(chunk, keywords)) {
                continue;
            }
            if (!appendChunkWithinBudget(selected, chunk, priorityBudget)) {
                break;
            }
            used[i] = true;
        }
        for (int i = 0; i < chunks.size(); i++) {
            if (used[i]) {
                continue;
            }
            if (!appendChunkWithinBudget(selected, chunks.get(i), maxChars)) {
                break;
            }
        }
        String value = selected.toString().trim();
        if (value.isBlank()) {
            return knowledgeText.substring(0, Math.min(maxChars, knowledgeText.length()));
        }
        return value;
    }

    private List<String> splitKnowledgeChunks(String knowledgeText) {
        return Arrays.stream(knowledgeText.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<String> extractKeywords(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        String normalizedMessage = userMessage
                .replace("请问", " ")
                .replace("是什么", " ")
                .replace("怎么", " ")
                .replace("如何", " ")
                .replace("吗", " ")
                .replace("么", " ")
                .replace("呢", " ");
        List<String> keywords = new ArrayList<>();
        for (String token : normalizedMessage.split("[\\s,，。！？?;；:：()（）\\[\\]【】]+")) {
            String value = token == null ? "" : token.trim().toLowerCase();
            if (value.length() >= 2) {
                keywords.add(value);
            }
        }
        return keywords.stream().distinct().toList();
    }

    private boolean matchesAnyKeyword(String chunk, List<String> keywords) {
        if (chunk == null || chunk.isBlank()) {
            return false;
        }
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        String lower = chunk.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean appendChunkWithinBudget(StringBuilder selected, String chunk, int budget) {
        if (chunk == null || chunk.isBlank() || budget <= 0) {
            return false;
        }
        int current = selected.length();
        int next = current == 0 ? chunk.length() : current + 2 + chunk.length();
        if (next > budget) {
            return false;
        }
        if (current > 0) {
            selected.append("\n\n");
        }
        selected.append(chunk);
        return true;
    }

    private long getTimeoutSeconds() {
        int ms = config.getAgentScopeRequestTimeoutMs();
        if (ms <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        long seconds = ms / 1000L;
        return Math.max(5, seconds);
    }

    String resolveAgentScopeUrl(SkillExecutionMode mode) {
        String baseUrl = config.getAgentScopeServerUrl();
        log.debug("Config agentscope.server.url={}", baseUrl);
        log.debug("Config agentscope.server.port={}", config.getAgentScopeServerPort());
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:" + config.getAgentScopeServerPort();
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        String executePath = mode == SkillExecutionMode.MULTI
                ? "/api/agentscope/multi-agent/execute"
                : "/api/agentscope/execute";

        if (url.contains("/api/agentscope")) {
            if (url.contains("/api/agentscope/execute") || url.contains("/api/agentscope/multi-agent/execute")) {
                if (mode == SkillExecutionMode.MULTI) {
                    return url.replace("/api/agentscope/execute", "/api/agentscope/multi-agent/execute");
                }
                return url.replace("/api/agentscope/multi-agent/execute", "/api/agentscope/execute");
            }
            if (url.endsWith("/api/agentscope")) {
                return url + (mode == SkillExecutionMode.MULTI ? "/multi-agent/execute" : "/execute");
            }
            return url + executePath;
        }

        return url + executePath;
    }

    String resolveSingleStreamUrl() {
        String baseUrl = config.getAgentScopeServerUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:" + config.getAgentScopeServerPort();
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        if (url.contains("/api/agentscope/execute")) {
            return url.replace("/api/agentscope/execute", "/api/agentscope/execute/stream");
        }
        if (url.contains("/api/agentscope/multi-agent/execute")) {
            return url.replace("/api/agentscope/multi-agent/execute", "/api/agentscope/execute/stream");
        }
        if (url.endsWith("/api/agentscope")) {
            return url + "/execute/stream";
        }
        if (url.contains("/api/agentscope")) {
            return url + "/execute/stream";
        }
        return url + "/api/agentscope/execute/stream";
    }

    private boolean isArtifactFirstSkill(String skillName, List<String> toolIds, List<String> toolGroups) {
        if (containsIgnoreCase(toolIds, "docx-generator")
                || containsIgnoreCase(toolIds, "docx")
                || containsIgnoreCase(toolIds, "pptx")
                || containsIgnoreCase(toolIds, "xlsx")
                || containsIgnoreCase(toolGroups, "document")
                || containsIgnoreCase(toolGroups, "docx")
                || containsIgnoreCase(toolGroups, "pptx")
                || containsIgnoreCase(toolGroups, "xlsx")
                || containsIgnoreCase(toolGroups, "pdf")) {
            return true;
        }
        String name = skillName != null ? skillName.toLowerCase() : "";
        return name.contains("docx") || name.contains("pptx") || name.contains("xlsx") || name.contains("pdf")
                || name.contains("文档") || name.contains("报告") || name.contains("演示");
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || values.isEmpty() || target == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.trim().equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private String adaptPromptTemplateForArtifactFirst(String skillName, String promptTemplate, boolean artifactFirst) {
        if (!artifactFirst) {
            return promptTemplate;
        }
        String name = (skillName == null || skillName.isBlank()) ? "文档技能" : skillName;
        return """
你是一个交付型技能执行器（%s）。
目标是直接产出最终文件成果，而不是解释如何编程实现。

强约束：
1) 禁止输出安装步骤、代码片段、脚本教程、伪代码。
2) 优先直接调用可用工具生成文档/表格/演示文件。
3) 当用户信息足够时必须立即生成；若少量信息缺失，可按合理默认值补齐后继续生成。
4) 最终回复必须是“交付结果”风格：简短结果 + 文件名/路径 + 关键内容摘要（不超过6行）。

用户需求：
{{input}}
""".formatted(name);
    }
}
