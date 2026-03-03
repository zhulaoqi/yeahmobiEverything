package com.yeahmobi.everything.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.ChatRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import net.jqwik.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for ChatServiceImpl AgentScope request construction.
 *
 * <p>Feature: yeahmobi-everything, Property 8: 大模型请求构造完整性</p>
 *
 * <p><b>Validates: Requirements 3.2</b></p>
 *
 * <p>For any user input text, Skill ID, and conversation history,
 * ChatService's constructed request should contain the user input
 * content and the Skill context information (prompt template).</p>
 */
class ChatServiceLlmRequestPropertyTest {

    private final ChatServiceImpl chatService;

    ChatServiceLlmRequestPropertyTest() {
        // Mock all dependencies — we only test the pure buildLlmRequestJson method
        ChatRepository chatRepository = mock(ChatRepository.class);
        CacheService cacheService = mock(CacheService.class);
        SkillRepository skillRepository = mock(SkillRepository.class);
        HttpClientUtil httpClientUtil = mock(HttpClientUtil.class);
        Config config = mock(Config.class);

        this.chatService = new ChatServiceImpl(
                chatRepository, cacheService, skillRepository, httpClientUtil, config);
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 8: 大模型请求构造完整性
    void agentScopeRequestContainsUserInputAndSkillContext(
            @ForAll("promptTemplates") String promptTemplate,
            @ForAll("userMessages") String userMessage
    ) {
        // **Validates: Requirements 3.2**

        // Act: build the AgentScope request JSON
        String requestJson = chatService.buildAgentScopeRequestJson(
                "skill-x",
                promptTemplate,
                userMessage,
                "Skill",
                List.of()
        );

        // Parse the JSON
        JsonObject request = JsonParser.parseString(requestJson).getAsJsonObject();
        assertEquals("skill-x", request.get("skillId").getAsString());
        assertEquals(userMessage, request.get("input").getAsString());
        assertEquals(promptTemplate == null ? "" : promptTemplate, request.get("promptTemplate").getAsString());
        assertTrue(request.has("skillName"));
        assertTrue(request.has("history"));
        assertTrue(request.has("userContext"));
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> promptTemplates() {
        // Generate prompt templates: mix of empty and non-empty strings
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.strings()
                        .ofMinLength(1)
                        .ofMaxLength(200)
                        .alpha()
                        .numeric()
        );
    }

    @Provide
    Arbitrary<String> userMessages() {
        // Generate non-empty user messages
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(200)
                .alpha()
                .numeric();
    }

    // no history needed for AgentScope request construction
}
