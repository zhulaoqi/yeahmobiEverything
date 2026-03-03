package com.yeahmobi.everything.knowledge;

import net.jqwik.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for knowledge file delete cascade.
 *
 * <p>Feature: yeahmobi-everything, Property 25: 知识库文件删除级联</p>
 *
 * <p><b>Validates: Requirements 14.4</b></p>
 *
 * <p>For any knowledge file bound to one or more Skills, deleting the file
 * should remove it from getAllFiles and from all Skills' getFilesForSkill results.</p>
 */
class KnowledgeDeleteCascadePropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 25: 知识库文件删除级联
    void deleteFileCascadesToBindings(
            @ForAll("skillIdLists") List<String> skillIds,
            @ForAll("knowledgeFiles") KnowledgeFile file
    ) {
        // **Validates: Requirements 14.4**

        InMemoryKnowledgeFileRepository fileRepo = new InMemoryKnowledgeFileRepository();
        InMemoryBindingRepository bindingRepo = new InMemoryBindingRepository();
        NoOpCacheService cacheService = new NoOpCacheService();
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(fileRepo, bindingRepo, cacheService);

        // Save the file
        fileRepo.saveFile(file);

        // Bind to multiple skills
        for (String skillId : skillIds) {
            service.bindFileToSkill(skillId, file.id());
        }

        // Delete the file
        service.deleteFile(file.id());

        // Verify: file is gone from getAllFiles
        List<KnowledgeFile> allFiles = service.getAllFiles();
        assertTrue(allFiles.stream().noneMatch(f -> f.id().equals(file.id())),
                "Deleted file should not appear in getAllFiles");

        // Verify: file is gone from all skills' bindings
        for (String skillId : skillIds) {
            List<KnowledgeFile> skillFiles = service.getFilesForSkill(skillId);
            assertTrue(skillFiles.stream().noneMatch(f -> f.id().equals(file.id())),
                    "Deleted file should not appear in getFilesForSkill for skill: " + skillId);
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<List<String>> skillIdLists() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(36).alpha().numeric()
                .list().ofMinSize(1).ofMaxSize(4)
                .filter(list -> list.stream().distinct().count() == list.size());
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
