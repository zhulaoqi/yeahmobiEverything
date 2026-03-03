package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.knowledge.KnowledgeBaseService;
import com.yeahmobi.everything.knowledge.KnowledgeFile;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.FeedbackRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for knowledge Skill auto-binding on creation.
 *
 * <p>Feature: yeahmobi-everything, Property 28: 模板创建自动绑定知识库</p>
 */
class KnowledgeSkillAutoBindPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 28: 模板创建自动绑定知识库
    void createKnowledgeSkill_autoBindsFiles(
            @ForAll("knowledgeTemplates") KnowledgeSkillTemplate template) {
        // **Validates: Requirements 15.8**

        // Track bindings: skillId -> set of fileIds
        Map<String, Set<String>> bindings = new HashMap<>();

        SkillRepository skillRepo = mock(SkillRepository.class);
        FeedbackRepository feedbackRepo = mock(FeedbackRepository.class);
        CacheService cacheService = mock(CacheService.class);
        KnowledgeBaseService knowledgeService = mock(KnowledgeBaseService.class);

        doNothing().when(skillRepo).saveSkill(any());

        // Track bind calls
        doAnswer(inv -> {
            String skillId = inv.getArgument(0);
            String fileId = inv.getArgument(1);
            bindings.computeIfAbsent(skillId, k -> new LinkedHashSet<>()).add(fileId);
            return null;
        }).when(knowledgeService).bindFileToSkill(anyString(), anyString());

        // Return bound file IDs when queried
        doAnswer(inv -> {
            String skillId = inv.getArgument(0);
            Set<String> fileIds = bindings.getOrDefault(skillId, Set.of());
            return fileIds.stream()
                    .map(fid -> new KnowledgeFile(fid, "file.txt", "txt", 100L, "upload", "text", 1000L, 1000L))
                    .toList();
        }).when(knowledgeService).getFilesForSkill(anyString());

        AdminServiceImpl service = new AdminServiceImpl(
                skillRepo, feedbackRepo, cacheService, knowledgeService);

        SkillIntegrationResult result = service.createKnowledgeSkill(template);

        assertTrue(result.success(), "createKnowledgeSkill should succeed");
        assertNotNull(result.skill());
        assertEquals(SkillKind.KNOWLEDGE_RAG, result.skill().kind(), "Knowledge skill should be KNOWLEDGE_RAG");

        // Verify all template file IDs were bound
        String createdSkillId = result.skill().id();
        List<KnowledgeFile> boundFiles = knowledgeService.getFilesForSkill(createdSkillId);
        Set<String> boundFileIds = new HashSet<>();
        for (KnowledgeFile f : boundFiles) {
            boundFileIds.add(f.id());
        }

        for (String expectedFileId : template.knowledgeFileIds()) {
            assertTrue(boundFileIds.contains(expectedFileId),
                    "File " + expectedFileId + " should be bound to the created skill");
        }
    }

    @Provide
    Arbitrary<KnowledgeSkillTemplate> knowledgeTemplates() {
        Arbitrary<String> names = Arbitraries.strings().ofMinLength(1).ofMaxLength(10).alpha();
        Arbitrary<String> descs = Arbitraries.strings().ofMinLength(1).ofMaxLength(15).alpha();
        Arbitrary<String> categories = Arbitraries.of("翻译", "写作", "开发", "数据");
        Arbitrary<SkillType> types = Arbitraries.of(SkillType.GENERAL, SkillType.INTERNAL);
        Arbitrary<List<String>> fileIdLists = Arbitraries.strings().ofMinLength(5).ofMaxLength(10).alpha()
                .list().ofMinSize(1).ofMaxSize(5);

        return Combinators.combine(names, descs, categories, types, fileIdLists)
                .as((name, desc, cat, type, fileIds) ->
                        new KnowledgeSkillTemplate(name, desc, cat, "icon.png", type, "prompt",
                                fileIds, null, SkillExecutionMode.SINGLE));
    }
}
