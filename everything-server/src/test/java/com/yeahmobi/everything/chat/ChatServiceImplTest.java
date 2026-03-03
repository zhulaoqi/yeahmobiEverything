package com.yeahmobi.everything.chat;

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
import com.yeahmobi.everything.skill.SkillType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChatServiceImpl}.
 * Uses Mockito to mock external dependencies (ChatRepository, CacheService,
 * SkillRepository, HttpClientUtil, Config).
 */
class ChatServiceImplTest {

    private ChatRepository chatRepository;
    private CacheService cacheService;
    private SkillRepository skillRepository;
    private HttpClientUtil httpClientUtil;
    private Config config;
    private KnowledgeBaseService knowledgeBaseService;
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatRepository = mock(ChatRepository.class);
        cacheService = mock(CacheService.class);
        skillRepository = mock(SkillRepository.class);
        httpClientUtil = mock(HttpClientUtil.class);
        knowledgeBaseService = mock(KnowledgeBaseService.class);

        Properties props = new Properties();
        props.setProperty("llm.api.timeout", "30000");
        props.setProperty("agentscope.server.url", "http://localhost:8099");
        config = Config.fromProperties(props);

        chatService = new ChatServiceImpl(
                chatRepository, cacheService, skillRepository, httpClientUtil, config, knowledgeBaseService
        );
    }

    // --- sendMessage tests ---

    @Test
    void sendMessageReturnsSuccessfulResponse() throws Exception {
        // Arrange
        SkillAdmin skill = createSkill("skill-1", SkillKind.PROMPT_ONLY, "You are a translator.");
        when(skillRepository.getSkill("skill-1")).thenReturn(Optional.of(skill));

        String llmResponse = buildAgentScopeResponseJson("Hello! How can I help?", "Plan", "Execution");
        when(httpClientUtil.post(anyString(), anyString(), anyMap())).thenReturn(llmResponse);

        // Act
        CompletableFuture<ChatResponse> future = chatService.sendMessage("skill-1", "Hi", List.of());
        ChatResponse response = future.get(5, TimeUnit.SECONDS);

        // Assert
        assertTrue(response.success());
        assertEquals("Hello! How can I help?", response.content());
        assertNull(response.errorMessage());
    }

    @Test
    void sendMessageIncludesSkillPromptTemplateAsSystemMessage() throws Exception {
        // Arrange
        SkillAdmin skill = createSkill("skill-1", SkillKind.PROMPT_ONLY, "You are a code assistant.");
        when(skillRepository.getSkill("skill-1")).thenReturn(Optional.of(skill));

        String llmResponse = buildAgentScopeResponseJson("Sure, here's the code.", null, null);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClientUtil.post(anyString(), bodyCaptor.capture(), anyMap())).thenReturn(llmResponse);

        // Act
        chatService.sendMessage("skill-1", "Write a function", List.of()).get(5, TimeUnit.SECONDS);

        // Assert
        String requestBody = bodyCaptor.getValue();
        JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
        assertEquals("You are a code assistant.", request.get("promptTemplate").getAsString());
    }

    @Test
    void sendMessageIncludesConversationHistory() throws Exception {
        // Arrange
        SkillAdmin skill = createSkill("skill-1", SkillKind.PROMPT_ONLY, "System prompt");
        when(skillRepository.getSkill("skill-1")).thenReturn(Optional.of(skill));

        List<ChatMessage> history = List.of(
                new ChatMessage("m1", "s1", "skill-1", "user", "First question", 1000L),
                new ChatMessage("m2", "s1", "skill-1", "assistant", "First answer", 2000L)
        );

        String llmResponse = buildAgentScopeResponseJson("Second answer", null, null);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClientUtil.post(anyString(), bodyCaptor.capture(), anyMap())).thenReturn(llmResponse);

        // Act
        chatService.sendMessage("skill-1", "Second question", history).get(5, TimeUnit.SECONDS);

        // Assert
        String requestBody = bodyCaptor.getValue();
        JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
        assertEquals("Second question", request.get("input").getAsString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendMessageIncludesAuthorizationHeader() throws Exception {
        // Arrange
        SkillAdmin skill = createSkill("skill-1", SkillKind.PROMPT_ONLY, "");
        when(skillRepository.getSkill("skill-1")).thenReturn(Optional.of(skill));

        String llmResponse = buildAgentScopeResponseJson("Response", null, null);
        ArgumentCaptor<java.util.Map<String, String>> headersCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        when(httpClientUtil.post(anyString(), anyString(), headersCaptor.capture())).thenReturn(llmResponse);

        // Act
        chatService.sendMessage("skill-1", "Hello", List.of()).get(5, TimeUnit.SECONDS);

        // Assert
        java.util.Map<String, String> headers = headersCaptor.getValue();
        assertEquals("application/json", headers.get("Content-Type"));
    }

    @Test
    void sendMessageReturnsErrorOnNetworkException() throws Exception {
        // Arrange
        SkillAdmin skill = createSkill("skill-1", SkillKind.PROMPT_ONLY, "");
        when(skillRepository.getSkill("skill-1")).thenReturn(Optional.of(skill));
        when(httpClientUtil.post(anyString(), anyString(), anyMap()))
                .thenThrow(new NetworkException("Connection refused"));

        // Act
        CompletableFuture<ChatResponse> future = chatService.sendMessage("skill-1", "Hello", List.of());
        ChatResponse response = future.get(5, TimeUnit.SECONDS);

        // Assert
        assertFalse(response.success());
        assertNull(response.content());
        assertTrue(response.errorMessage().contains("服务不可用"));
    }

    @Test
    void sendMessageHandlesMissingSkillGracefully() throws Exception {
        // Arrange
        when(skillRepository.getSkill("nonexistent")).thenReturn(Optional.empty());

        String llmResponse = buildAgentScopeResponseJson("Response without skill context", null, null);
        when(httpClientUtil.post(anyString(), anyString(), anyMap())).thenReturn(llmResponse);

        // Act
        CompletableFuture<ChatResponse> future = chatService.sendMessage("nonexistent", "Hello", List.of());
        ChatResponse response = future.get(5, TimeUnit.SECONDS);

        // Assert
        assertTrue(response.success());
        assertEquals("Response without skill context", response.content());
    }

    @Test
    void sendMessageInjectsKnowledgeForKnowledgeRagSkill() throws Exception {
        // Arrange
        SkillAdmin skill = createSkill("skill-rag", SkillKind.KNOWLEDGE_RAG, "You are a knowledge assistant.");
        when(skillRepository.getSkill("skill-rag")).thenReturn(Optional.of(skill));
        when(cacheService.getCachedKnowledgeText("skill-rag"))
                .thenReturn(Optional.of("Knowledge content here"));

        String llmResponse = buildAgentScopeResponseJson("Answer based on knowledge", null, null);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClientUtil.post(anyString(), bodyCaptor.capture(), anyMap())).thenReturn(llmResponse);

        // Act
        chatService.sendMessage("skill-rag", "What is X?", List.of()).get(5, TimeUnit.SECONDS);

        // Assert
        String requestBody = bodyCaptor.getValue();
        JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
        String content = request.get("input").getAsString();
        assertTrue(content.contains("Knowledge content here"));
        assertTrue(content.contains("What is X?"));
    }

    @Test
    void sendMessageDoesNotInjectKnowledgeForPromptOnlySkill() throws Exception {
        // Arrange
        SkillAdmin skill = createSkill("skill-prompt", SkillKind.PROMPT_ONLY, "You are a translator.");
        when(skillRepository.getSkill("skill-prompt")).thenReturn(Optional.of(skill));

        String llmResponse = buildAgentScopeResponseJson("Translation result", null, null);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClientUtil.post(anyString(), bodyCaptor.capture(), anyMap())).thenReturn(llmResponse);

        // Act
        chatService.sendMessage("skill-prompt", "Translate this", List.of()).get(5, TimeUnit.SECONDS);

        // Assert
        String requestBody = bodyCaptor.getValue();
        JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
        assertEquals("Translate this", request.get("input").getAsString());

        // CacheService should not be called for knowledge text
        verify(cacheService, never()).getCachedKnowledgeText(anyString());
    }

    @Test
    void sendMessageWithEmptyHistory() throws Exception {
        // Arrange
        SkillAdmin skill = createSkill("skill-1", SkillKind.PROMPT_ONLY, "System");
        when(skillRepository.getSkill("skill-1")).thenReturn(Optional.of(skill));

        String llmResponse = buildAgentScopeResponseJson("Response", null, null);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClientUtil.post(anyString(), bodyCaptor.capture(), anyMap())).thenReturn(llmResponse);

        // Act
        chatService.sendMessage("skill-1", "Hello", List.of()).get(5, TimeUnit.SECONDS);

        // Assert
        String requestBody = bodyCaptor.getValue();
        JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
        assertEquals("Hello", request.get("input").getAsString());
    }

    @Test
    void sendMessageWithNullHistory() throws Exception {
        // Arrange
        SkillAdmin skill = createSkill("skill-1", SkillKind.PROMPT_ONLY, "System");
        when(skillRepository.getSkill("skill-1")).thenReturn(Optional.of(skill));

        String llmResponse = buildAgentScopeResponseJson("Response", null, null);
        when(httpClientUtil.post(anyString(), anyString(), anyMap())).thenReturn(llmResponse);

        // Act
        CompletableFuture<ChatResponse> future = chatService.sendMessage("skill-1", "Hello", null);
        ChatResponse response = future.get(5, TimeUnit.SECONDS);

        // Assert
        assertTrue(response.success());
    }

    @Test
    void sendMessageWithEmptyPromptTemplateOmitsSystemMessage() throws Exception {
        // Arrange
        SkillAdmin skill = createSkill("skill-1", SkillKind.PROMPT_ONLY, "");
        when(skillRepository.getSkill("skill-1")).thenReturn(Optional.of(skill));

        String llmResponse = buildAgentScopeResponseJson("Response", null, null);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(httpClientUtil.post(anyString(), bodyCaptor.capture(), anyMap())).thenReturn(llmResponse);

        // Act
        chatService.sendMessage("skill-1", "Hello", List.of()).get(5, TimeUnit.SECONDS);

        // Assert
        String requestBody = bodyCaptor.getValue();
        JsonObject request = JsonParser.parseString(requestBody).getAsJsonObject();
        assertEquals("", request.get("promptTemplate").getAsString());
    }

    // --- buildContextWithKnowledge tests ---

    @Test
    void buildContextWithKnowledgeReturnsCachedText() {
        // Arrange
        when(cacheService.getCachedKnowledgeText("skill-1"))
                .thenReturn(Optional.of("Cached knowledge content"));

        // Act
        String context = chatService.buildContextWithKnowledge("skill-1", "What is X?");

        // Assert
        assertTrue(context.contains("Cached knowledge content"));
        assertTrue(context.contains("What is X?"));
    }

    @Test
    void buildContextWithKnowledgeReturnsUserMessageWhenNoKnowledge() {
        // Arrange
        when(cacheService.getCachedKnowledgeText("skill-1")).thenReturn(Optional.empty());
        when(knowledgeBaseService.getMergedKnowledgeText("skill-1")).thenReturn("");

        // Act
        String context = chatService.buildContextWithKnowledge("skill-1", "Simple question");

        // Assert
        assertTrue(context.contains("Simple question"));
        assertTrue(context.contains("知识库未命中或尚未绑定资料"));
    }

    @Test
    void buildContextWithKnowledgeCachesResultAfterDatabaseFetch() {
        // Arrange
        when(cacheService.getCachedKnowledgeText("skill-1")).thenReturn(Optional.empty());
        when(knowledgeBaseService.getMergedKnowledgeText("skill-1"))
                .thenReturn("知识库中的产品政策内容");

        // Act
        String context = chatService.buildContextWithKnowledge("skill-1", "Question");

        // Assert
        assertTrue(context.contains("知识库中的产品政策内容"));
        verify(cacheService).cacheKnowledgeText(eq("skill-1"), eq("知识库中的产品政策内容"), anyLong());
    }

    @Test
    void buildContextWithKnowledgeHandlesRedisFailureGracefully() {
        // Arrange
        when(cacheService.getCachedKnowledgeText("skill-1"))
                .thenThrow(new RuntimeException("Redis connection failed"));
        when(knowledgeBaseService.getMergedKnowledgeText("skill-1")).thenReturn("");

        // Act
        String context = chatService.buildContextWithKnowledge("skill-1", "Question");

        // Assert - should fall back gracefully
        assertTrue(context.contains("Question"));
        assertTrue(context.contains("知识库未命中或尚未绑定资料"));
    }

    @Test
    void buildContextWithKnowledgeHandlesCacheWriteFailure() {
        // Arrange
        when(cacheService.getCachedKnowledgeText("skill-1")).thenReturn(Optional.empty());
        when(knowledgeBaseService.getMergedKnowledgeText("skill-1")).thenReturn("A");
        doThrow(new RuntimeException("Redis write failed"))
                .when(cacheService).cacheKnowledgeText(anyString(), anyString(), anyLong());

        // Act - should not throw
        String context = chatService.buildContextWithKnowledge("skill-1", "Question");

        // Assert
        assertTrue(context.contains("A"));
    }

    @Test
    void buildContextWithKnowledgeReturnsUserMessageWhenCachedTextIsEmpty() {
        // Arrange
        when(cacheService.getCachedKnowledgeText("skill-1")).thenReturn(Optional.of(""));
        when(knowledgeBaseService.getMergedKnowledgeText("skill-1")).thenReturn("");

        // Act
        String context = chatService.buildContextWithKnowledge("skill-1", "Question");

        // Assert - empty cached text should be treated as no knowledge
        assertTrue(context.contains("Question"));
        assertTrue(context.contains("知识库未命中或尚未绑定资料"));
    }

    @Test
    void buildContextWithKnowledgeIncludesKnowledgeSources() {
        when(cacheService.getCachedKnowledgeText("skill-1")).thenReturn(Optional.empty());
        when(knowledgeBaseService.getMergedKnowledgeText("skill-1")).thenReturn("公司制度正文");
        when(knowledgeBaseService.getFilesForSkill("skill-1")).thenReturn(List.of(
                new KnowledgeFile("f1", "员工手册.md", "md", 12, "upload", "x", 1L, 1L),
                new KnowledgeFile("f2", "报销制度.pdf", "pdf", 20, "upload", "y", 1L, 1L)
        ));

        String context = chatService.buildContextWithKnowledge("skill-1", "报销怎么走流程");

        assertTrue(context.contains("知识来源"));
        assertTrue(context.contains("员工手册.md"));
        assertTrue(context.contains("报销制度.pdf"));
    }

    @Test
    void buildContextWithKnowledgePrioritizesKeywordChunksWhenTruncating() {
        when(cacheService.getCachedKnowledgeText("skill-1")).thenReturn(Optional.empty());

        String largeNoise = "通用说明".repeat(2000);
        String targetChunk = "报销审批流程：先提交申请，再由直属主管审批。";
        String knowledge = largeNoise + "\n\n" + "其他制度".repeat(2000) + "\n\n" + targetChunk;
        when(knowledgeBaseService.getMergedKnowledgeText("skill-1")).thenReturn(knowledge);

        String context = chatService.buildContextWithKnowledge("skill-1", "报销审批流程是什么");
        assertTrue(context.contains(targetChunk));
    }

    // --- Delegation tests ---

    @Test
    void getChatHistoryDelegatesToRepository() {
        // Arrange
        List<ChatMessage> expected = List.of(
                new ChatMessage("m1", "s1", "skill-1", "user", "Hello", 1000L)
        );
        when(chatRepository.getHistory("s1")).thenReturn(expected);

        // Act
        List<ChatMessage> result = chatService.getChatHistory("s1");

        // Assert
        assertEquals(expected, result);
        verify(chatRepository).getHistory("s1");
    }

    @Test
    void saveMessageDelegatesToRepository() {
        // Arrange
        ChatMessage message = new ChatMessage("m1", "s1", "skill-1", "user", "Hello", 1000L);

        // Act
        chatService.saveMessage(message);

        // Assert
        verify(chatRepository).saveMessage(message);
    }

    @Test
    void getAllSessionsDelegatesToRepository() {
        // Arrange
        List<ChatSession> expected = List.of(
                new ChatSession("s1", "skill-1", "Translation", "Last msg", 1000L)
        );
        when(chatRepository.getAllSessions("user-1")).thenReturn(expected);

        // Act
        List<ChatSession> result = chatService.getAllSessions("user-1");

        // Assert
        assertEquals(expected, result);
        verify(chatRepository).getAllSessions("user-1");
    }

    @Test
    void searchSessionsDelegatesToRepository() {
        // Arrange
        List<ChatSession> expected = List.of(
                new ChatSession("s1", "skill-1", "Translation", "keyword match", 1000L)
        );
        when(chatRepository.searchSessions("keyword", "user-1")).thenReturn(expected);

        // Act
        List<ChatSession> result = chatService.searchSessions("keyword", "user-1");

        // Assert
        assertEquals(expected, result);
        verify(chatRepository).searchSessions("keyword", "user-1");
    }

    @Test
    void deleteSessionDelegatesToRepository() {
        // Act
        chatService.deleteSession("s1");

        // Assert
        verify(chatRepository).deleteSession("s1");
    }

    // --- buildAgentScopeRequestJson tests ---

    @Test
    void buildAgentScopeRequestJsonProducesValidJson() {
        // Act
        String json = chatService.buildAgentScopeRequestJson(
                "skill-1", "System prompt", "User message", "Skill", List.of()
        );

        // Assert
        JsonObject request = JsonParser.parseString(json).getAsJsonObject();
        assertNotNull(request.get("skillId"));
        assertNotNull(request.get("input"));
        assertNotNull(request.get("promptTemplate"));
        assertNotNull(request.get("history"));
        assertNotNull(request.get("userContext"));
    }

    @Test
    void buildAgentScopeRequestJsonContainsUserInputAndSkillContext() {
        // Act
        String json = chatService.buildAgentScopeRequestJson(
                "skill-1", "You are a translator", "Translate hello", "Skill", List.of()
        );

        // Assert
        assertTrue(json.contains("You are a translator"));
        assertTrue(json.contains("Translate hello"));
    }

    // --- parseAgentScopeResponse tests ---

    @Test
    void parseAgentScopeResponseExtractsContent() {
        // Arrange
        String responseJson = buildAgentScopeResponseJson("Extracted content", "Plan", "Execution");

        // Act
        ChatResponse response = chatService.parseAgentScopeResponse(responseJson);

        // Assert
        assertTrue(response.success());
        assertEquals("Extracted content", response.content());
        assertEquals("Plan", response.plan());
        assertEquals("Execution", response.execution());
    }

    @Test
    void parseAgentScopeResponseReturnsErrorOnInvalidJson() {
        // Act
        ChatResponse response = chatService.parseAgentScopeResponse("not valid json");

        // Assert
        assertFalse(response.success());
    }

    @Test
    void parseAgentScopeResponseReturnsErrorOnMissingSuccess() {
        // Arrange
        String responseJson = "{\"id\": \"test\"}";

        // Act
        ChatResponse response = chatService.parseAgentScopeResponse(responseJson);

        // Assert
        assertFalse(response.success());
    }

    // ---- Helper methods ----

    private SkillAdmin createSkill(String id, SkillKind kind, String promptTemplate) {
        return new SkillAdmin(
                id, "Test Skill", "Description", "icon.png", "general",
                true,
                "",
                List.of(),
                null,
                "test",
                "en",
                "basic",
                SkillType.GENERAL, kind, promptTemplate,
                SkillExecutionMode.SINGLE, System.currentTimeMillis()
        );
    }

    private String buildAgentScopeResponseJson(String content, String plan, String execution) {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("content", content);
        response.addProperty("plan", plan);
        response.addProperty("execution", execution);
        return response.toString();
    }
}
