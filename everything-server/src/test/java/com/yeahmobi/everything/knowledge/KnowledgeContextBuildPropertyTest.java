package com.yeahmobi.everything.knowledge;

import net.jqwik.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for knowledge context building completeness.
 *
 * <p>Feature: yeahmobi-everything, Property 22: 知识库上下文构建完整性</p>
 *
 * <p><b>Validates: Requirements 13.3</b></p>
 *
 * <p>Verifies that for any Skill with bound knowledge files and any user input,
 * getMergedKnowledgeText returns text containing all bound files' extracted text,
 * and a context string built from it contains both the user input and knowledge content.</p>
 */
class KnowledgeContextBuildPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 22: 知识库上下文构建完整性
    void contextContainsUserInputAndKnowledge(
            @ForAll("skillIds") String skillId,
            @ForAll("knowledgeFileLists") List<KnowledgeFile> files,
            @ForAll("userMessages") String userMessage
    ) {
        // **Validates: Requirements 13.3**

        InMemoryKnowledgeFileRepository fileRepo = new InMemoryKnowledgeFileRepository();
        InMemoryBindingRepository bindingRepo = new InMemoryBindingRepository();
        NoOpCacheService cacheService = new NoOpCacheService();
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(fileRepo, bindingRepo, cacheService);

        // Save and bind files
        for (KnowledgeFile f : files) {
            fileRepo.saveFile(f);
            service.bindFileToSkill(skillId, f.id());
        }

        // Get merged knowledge text
        String mergedText = service.getMergedKnowledgeText(skillId);

        // Verify: merged text contains each file's extracted text
        for (KnowledgeFile f : files) {
            if (f.extractedText() != null && !f.extractedText().isEmpty()) {
                assertTrue(mergedText.contains(f.extractedText()),
                        "Merged text should contain extracted text from file: " + f.id());
            }
        }

        // Build context string (simulating what ChatServiceImpl.buildContextWithKnowledge does)
        String context;
        if (!mergedText.isEmpty()) {
            context = "以下是相关知识库内容，请参考回答用户问题：\n\n"
                    + mergedText
                    + "\n\n用户问题：" + userMessage;
        } else {
            context = userMessage;
        }

        // Verify: context contains user input
        assertTrue(context.contains(userMessage),
                "Context should contain the user message");

        // Verify: context contains knowledge content when files have text
        boolean hasText = files.stream()
                .anyMatch(f -> f.extractedText() != null && !f.extractedText().isEmpty());
        if (hasText) {
            for (KnowledgeFile f : files) {
                if (f.extractedText() != null && !f.extractedText().isEmpty()) {
                    assertTrue(context.contains(f.extractedText()),
                            "Context should contain knowledge text from file: " + f.id());
                }
            }
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> skillIds() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(36).alpha().numeric();
    }

    @Provide
    Arbitrary<String> userMessages() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(100).alpha();
    }

    @Provide
    Arbitrary<List<KnowledgeFile>> knowledgeFileLists() {
        return knowledgeFiles().list().ofMinSize(1).ofMaxSize(4)
                .filter(list -> list.stream().map(KnowledgeFile::id).distinct().count() == list.size());
    }

    @Provide
    Arbitrary<KnowledgeFile> knowledgeFiles() {
        Arbitrary<String> ids = Arbitraries.strings().ofMinLength(5).ofMaxLength(36).alpha().numeric();
        Arbitrary<String> texts = Arbitraries.strings().ofMinLength(1).ofMaxLength(50).alpha();

        return Combinators.combine(ids, texts)
                .as((id, text) -> new KnowledgeFile(
                        id, "doc.txt", "txt", 100L, "upload", text,
                        System.currentTimeMillis(), System.currentTimeMillis()));
    }
}
