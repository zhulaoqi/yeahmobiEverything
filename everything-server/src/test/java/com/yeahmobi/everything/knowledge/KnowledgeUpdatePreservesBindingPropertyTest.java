package com.yeahmobi.everything.knowledge;

import net.jqwik.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for knowledge file update preserving bindings.
 *
 * <p>Feature: yeahmobi-everything, Property 24: 知识库文件更新保留绑定</p>
 *
 * <p><b>Validates: Requirements 14.3</b></p>
 */
class KnowledgeUpdatePreservesBindingPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 24: 知识库文件更新保留绑定
    void updateFilePreservesBindings(
            @ForAll("skillIds") String skillId,
            @ForAll("originalTexts") String originalText,
            @ForAll("updatedTexts") String updatedText
    ) throws Exception {
        // **Validates: Requirements 14.3**

        InMemoryKnowledgeFileRepository fileRepo = new InMemoryKnowledgeFileRepository();
        InMemoryBindingRepository bindingRepo = new InMemoryBindingRepository();
        NoOpCacheService cacheService = new NoOpCacheService();
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(fileRepo, bindingRepo, cacheService);

        // Upload original file
        Path tempFile = Files.createTempFile("test-original-", ".txt");
        try {
            Files.writeString(tempFile, originalText);
            KnowledgeFile original = service.uploadFile(tempFile.toFile());

            // Bind to skill
            service.bindFileToSkill(skillId, original.id());

            // Verify binding exists
            List<KnowledgeFile> boundBefore = service.getFilesForSkill(skillId);
            assertTrue(boundBefore.stream().anyMatch(f -> f.id().equals(original.id())),
                    "File should be bound before update");

            // Update the file with new content
            Path newTempFile = Files.createTempFile("test-updated-", ".txt");
            try {
                Files.writeString(newTempFile, updatedText);
                KnowledgeFile updated = service.updateFile(original.id(), newTempFile.toFile());

                // Verify: binding is preserved
                List<KnowledgeFile> boundAfter = service.getFilesForSkill(skillId);
                assertTrue(boundAfter.stream().anyMatch(f -> f.id().equals(original.id())),
                        "Binding should be preserved after file update");

                // Verify: extracted text is updated
                assertEquals(updatedText, updated.extractedText(),
                        "Extracted text should be updated to new content");
            } finally {
                Files.deleteIfExists(newTempFile);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> skillIds() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(36).alpha().numeric();
    }

    @Provide
    Arbitrary<String> originalTexts() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(100)
                .filter(s -> !s.isEmpty());
    }

    @Provide
    Arbitrary<String> updatedTexts() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(100)
                .filter(s -> !s.isEmpty());
    }
}
