package com.yeahmobi.everything.knowledge;

import net.jqwik.api.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for knowledge file unbinding correctness.
 *
 * <p>Feature: yeahmobi-everything, Property 20: 知识库文件解绑正确性</p>
 *
 * <p><b>Validates: Requirements 13.4</b></p>
 */
class KnowledgeUnbindPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 20: 知识库文件解绑正确性
    void unbindRemovesOnlyTargetFile(
            @ForAll("skillIds") String skillId,
            @ForAll("knowledgeFilePairs") List<KnowledgeFile> files,
            @ForAll("unbindIndexProvider") int unbindIdx
    ) {
        // **Validates: Requirements 13.4**
        if (files.size() < 2) return; // need at least 2 files
        int idx = Math.abs(unbindIdx) % files.size();

        InMemoryKnowledgeFileRepository fileRepo = new InMemoryKnowledgeFileRepository();
        InMemoryBindingRepository bindingRepo = new InMemoryBindingRepository();
        NoOpCacheService cacheService = new NoOpCacheService();
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(fileRepo, bindingRepo, cacheService);

        // Save and bind all files
        for (KnowledgeFile f : files) {
            fileRepo.saveFile(f);
            service.bindFileToSkill(skillId, f.id());
        }

        // Unbind one file
        KnowledgeFile toUnbind = files.get(idx);
        service.unbindFileFromSkill(skillId, toUnbind.id());

        // Verify: unbound file is not in result
        List<KnowledgeFile> result = service.getFilesForSkill(skillId);
        Set<String> resultIds = result.stream().map(KnowledgeFile::id).collect(Collectors.toSet());

        assertFalse(resultIds.contains(toUnbind.id()),
                "Unbound file should not appear in getFilesForSkill");

        // Verify: all other files still present
        for (int i = 0; i < files.size(); i++) {
            if (i != idx) {
                assertTrue(resultIds.contains(files.get(i).id()),
                        "File at index " + i + " should still be bound");
            }
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> skillIds() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(36).alpha().numeric();
    }

    @Provide
    Arbitrary<List<KnowledgeFile>> knowledgeFilePairs() {
        return knowledgeFiles().list().ofMinSize(2).ofMaxSize(5)
                .filter(list -> list.stream().map(KnowledgeFile::id).distinct().count() == list.size());
    }

    @Provide("unbindIndexProvider")
    Arbitrary<Integer> unbindIndex() {
        return Arbitraries.integers().between(0, 100);
    }

    @Provide
    Arbitrary<KnowledgeFile> knowledgeFiles() {
        Arbitrary<String> ids = Arbitraries.strings().ofMinLength(5).ofMaxLength(36).alpha().numeric();
        Arbitrary<String> names = Arbitraries.of("doc.txt", "guide.md", "manual.pdf", "faq.txt", "notes.md");
        Arbitrary<String> texts = Arbitraries.strings().ofMinLength(1).ofMaxLength(50).alpha();

        return Combinators.combine(ids, names, texts)
                .as((id, name, text) -> new KnowledgeFile(
                        id, name, KnowledgeBaseServiceImpl.getExtension(name),
                        100L, "upload", text,
                        System.currentTimeMillis(), System.currentTimeMillis()));
    }
}
